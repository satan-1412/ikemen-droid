package snd_module

import (
	"bytes"
	"encoding/binary"
	"fmt"
	"io"
	"os"
	"strings"

	"github.com/gopxl/beep/v2"
	"github.com/gopxl/beep/v2/flac"
	"github.com/gopxl/beep/v2/mp3"
	"github.com/gopxl/beep/v2/vorbis"
)

// ==========================================
// 🛠️ 基础结构定义
// ==========================================

type SndNodeInfo struct {
	Group int32
	Item  int32
}

// ==========================================
// 🎵 核心解包算法
// ==========================================

func ExtractAllNodes(filename string) ([]SndNodeInfo, error) {
	f, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	buf := make([]byte, 12)
	n, err := f.Read(buf)
	if err != nil {
		return nil, err
	}

	if string(buf[:n]) != "ElecbyteSnd\x00" {
		return nil, fmt.Errorf("invalid SND header")
	}

	var ver, ver2 uint16
	var numberOfSounds, subHeaderOffset uint32

	binary.Read(f, binary.LittleEndian, &ver)
	binary.Read(f, binary.LittleEndian, &ver2)
	binary.Read(f, binary.LittleEndian, &numberOfSounds)
	binary.Read(f, binary.LittleEndian, &subHeaderOffset)

	nodes := make([]SndNodeInfo, 0, numberOfSounds)

	for i := uint32(0); i < numberOfSounds; i++ {
		if subHeaderOffset == 0 {
			break
		}
		f.Seek(int64(subHeaderOffset), io.SeekStart)
		var nextSubHeaderOffset, subFileLength uint32
		var num [2]int32

		binary.Read(f, binary.LittleEndian, &nextSubHeaderOffset)
		binary.Read(f, binary.LittleEndian, &subFileLength)
		binary.Read(f, binary.LittleEndian, &num)

		nodes = append(nodes, SndNodeInfo{Group: num[0], Item: num[1]})
		subHeaderOffset = nextSubHeaderOffset
	}
	return nodes, nil
}

func ExtractWav(filename string, targetGroup int32, targetItem int32) ([]byte, error) {
	f, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	buf := make([]byte, 12)
	f.Read(buf)
	if !bytes.HasPrefix(buf, []byte("ElecbyteSnd")) {
		return nil, fmt.Errorf("invalid SND header")
	}

	f.Seek(16, io.SeekStart)
	var numberOfSounds, subHeaderOffset uint32
	binary.Read(f, binary.LittleEndian, &numberOfSounds)
	binary.Read(f, binary.LittleEndian, &subHeaderOffset)

	for i := uint32(0); i < numberOfSounds; i++ {
		if subHeaderOffset == 0 {
			break
		}
		f.Seek(int64(subHeaderOffset), io.SeekStart)
		var nextSubHeaderOffset, subFileLength uint32
		var num [2]int32

		binary.Read(f, binary.LittleEndian, &nextSubHeaderOffset)
		binary.Read(f, binary.LittleEndian, &subFileLength)
		binary.Read(f, binary.LittleEndian, &num)

		if num[0] == targetGroup && num[1] == targetItem {
			wavData := make([]byte, subFileLength)
			f.Read(wavData)
			return wavData, nil
		}
		subHeaderOffset = nextSubHeaderOffset
	}
	return nil, fmt.Errorf("sound %d,%d not found", targetGroup, targetItem)
}

// ==========================================
// 🚀 核心黑科技：底层无损转码器 (解决引擎静音Bug)
// ==========================================

func getWavData(audioPath string) ([]byte, error) {
	f, err := os.Open(audioPath)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	lowerPath := strings.ToLower(audioPath)
	
	// 如果本来就是 WAV，直接返回，跳过转码
	if strings.HasSuffix(lowerPath, ".wav") {
		return io.ReadAll(f)
	}

	var streamer beep.StreamSeekCloser
	var format beep.Format

	// 智能识别源格式并调用对应解码器
	if strings.HasSuffix(lowerPath, ".mp3") {
		streamer, format, err = mp3.Decode(f)
	} else if strings.HasSuffix(lowerPath, ".ogg") {
		streamer, format, err = vorbis.Decode(f)
	} else if strings.HasSuffix(lowerPath, ".flac") {
		streamer, format, err = flac.Decode(f)
	} else {
		// 未知后缀，尝试直接按原数据读取
		return io.ReadAll(f)
	}

	if err != nil {
		return nil, fmt.Errorf("解码失败: %v", err)
	}
	defer streamer.Close()

	numFrames := streamer.Len()
	if numFrames <= 0 {
		return nil, fmt.Errorf("音频长度无效")
	}

	// 强制构建 16位 PCM WAV 文件头，这是引擎唯一认死理的格式
	numChannels := format.NumChannels
	sampleRate := uint32(format.SampleRate)
	bitsPerSample := 16
	byteRate := sampleRate * uint32(numChannels) * uint32(bitsPerSample/8)
	blockAlign := uint16(numChannels) * uint16(bitsPerSample/8)
	dataSize := uint32(numFrames) * uint32(blockAlign)
	fileSize := 36 + dataSize

	buf := new(bytes.Buffer)
	buf.WriteString("RIFF")
	binary.Write(buf, binary.LittleEndian, uint32(fileSize))
	buf.WriteString("WAVEfmt ")
	binary.Write(buf, binary.LittleEndian, uint32(16))
	binary.Write(buf, binary.LittleEndian, uint16(1)) // PCM
	binary.Write(buf, binary.LittleEndian, uint16(numChannels))
	binary.Write(buf, binary.LittleEndian, uint32(sampleRate))
	binary.Write(buf, binary.LittleEndian, uint32(byteRate))
	binary.Write(buf, binary.LittleEndian, uint16(blockAlign))
	binary.Write(buf, binary.LittleEndian, uint16(bitsPerSample))
	buf.WriteString("data")
	binary.Write(buf, binary.LittleEndian, uint32(dataSize))

	// 将音频流提取并强制转换为 16位 纯净 PCM 写入
	samples := make([][2]float64, 512)
	for {
		n, ok := streamer.Stream(samples)
		if n == 0 || !ok {
			break
		}
		for i := 0; i < n; i++ {
			for c := 0; c < numChannels; c++ {
				val := samples[i][c]
				if val < -1.0 { val = -1.0 }
				if val > 1.0 { val = 1.0 }
				intVal := int16(val * 32767.0)
				binary.Write(buf, binary.LittleEndian, intVal)
			}
		}
	}

	return buf.Bytes(), nil
}

// ==========================================
// 🛠️ 独家底层写入机制：带转码的顺序重建
// ==========================================

func ReplaceAudioWithWav(sndPath string, targetGroup int32, targetItem int32, audioPath string) error {
	// 1. 无损转码：自动把 MP3/OGG 变成引擎需要的纯种 WAV
	audioData, err := getWavData(audioPath)
	if err != nil {
		// 如果转码失败（比如奇怪的未知文件），兜底方案直接读取原文件
		audioData, err = os.ReadFile(audioPath)
		if err != nil {
			return fmt.Errorf("读取文件失败: %v", err)
		}
	}

	f, err := os.Open(sndPath)
	if err != nil {
		return fmt.Errorf("无法打开SND文件: %v", err)
	}

	buf := make([]byte, 12)
	f.Read(buf)
	if string(buf) != "ElecbyteSnd\x00" {
		f.Close()
		return fmt.Errorf("非法的 SND 格式")
	}

	var ver, ver2 uint16
	var numberOfSounds, subHeaderOffset uint32
	binary.Read(f, binary.LittleEndian, &ver)
	binary.Read(f, binary.LittleEndian, &ver2)
	binary.Read(f, binary.LittleEndian, &numberOfSounds)
	binary.Read(f, binary.LittleEndian, &subHeaderOffset)

	type Node struct {
		Group int32
		Item  int32
		Data  []byte
	}
	var nodes []Node

	for i := uint32(0); i < numberOfSounds; i++ {
		if subHeaderOffset == 0 {
			break
		}
		f.Seek(int64(subHeaderOffset), io.SeekStart)
		var nextSubHeaderOffset, subFileLength uint32
		var num [2]int32
		binary.Read(f, binary.LittleEndian, &nextSubHeaderOffset)
		binary.Read(f, binary.LittleEndian, &subFileLength)
		binary.Read(f, binary.LittleEndian, &num)

		data := make([]byte, subFileLength)
		f.Read(data)

		// 将转码后的纯净音频替换进去
		if num[0] == targetGroup && num[1] == targetItem {
			data = audioData
		}
		nodes = append(nodes, Node{Group: num[0], Item: num[1], Data: data})
		subHeaderOffset = nextSubHeaderOffset
	}
	f.Close()

	// 2. 全量物理重写，严格锁定 24 字节头部
	out, err := os.Create(sndPath)
	if err != nil {
		return err
	}
	defer out.Close()

	out.Write([]byte("ElecbyteSnd\x00"))
	binary.Write(out, binary.LittleEndian, ver)
	binary.Write(out, binary.LittleEndian, ver2)
	binary.Write(out, binary.LittleEndian, uint32(len(nodes)))
	
	if len(nodes) > 0 {
		binary.Write(out, binary.LittleEndian, uint32(24))
	} else {
		binary.Write(out, binary.LittleEndian, uint32(0))
	}

	currentOffset := uint32(24)
	for i, n := range nodes {
		nextOffset := uint32(0)
		if i < len(nodes)-1 {
			nextOffset = currentOffset + 16 + uint32(len(n.Data))
		}
		out.Seek(int64(currentOffset), io.SeekStart)
		binary.Write(out, binary.LittleEndian, nextOffset)
		binary.Write(out, binary.LittleEndian, uint32(len(n.Data)))
		binary.Write(out, binary.LittleEndian, n.Group)
		binary.Write(out, binary.LittleEndian, n.Item)
		out.Write(n.Data)
		currentOffset = nextOffset
	}
	return nil
}

func AddAudioWithWav(sndPath string, targetGroup int32, targetItem int32, audioPath string) error {
	audioData, err := getWavData(audioPath)
	if err != nil {
		audioData, err = os.ReadFile(audioPath)
		if err != nil { return fmt.Errorf("读取文件失败: %v", err) }
	}

	f, err := os.Open(sndPath)
	if err != nil { return fmt.Errorf("无法打开SND文件: %v", err) }

	buf := make([]byte, 12)
	f.Read(buf)
	if string(buf) != "ElecbyteSnd\x00" { f.Close(); return fmt.Errorf("非法的 SND 格式") }

	var ver, ver2 uint16
	var numberOfSounds, subHeaderOffset uint32
	binary.Read(f, binary.LittleEndian, &ver)
	binary.Read(f, binary.LittleEndian, &ver2)
	binary.Read(f, binary.LittleEndian, &numberOfSounds)
	binary.Read(f, binary.LittleEndian, &subHeaderOffset)

	type Node struct { Group, Item int32; Data []byte }
	var nodes []Node

	for i := uint32(0); i < numberOfSounds; i++ {
		if subHeaderOffset == 0 { break }
		f.Seek(int64(subHeaderOffset), io.SeekStart)
		var nextSubHeaderOffset, subFileLength uint32
		var num [2]int32
		binary.Read(f, binary.LittleEndian, &nextSubHeaderOffset)
		binary.Read(f, binary.LittleEndian, &subFileLength)
		binary.Read(f, binary.LittleEndian, &num)

		if num[0] == targetGroup && num[1] == targetItem {
			f.Close()
			return fmt.Errorf("编号已被占用，请先删除或使用替换功能")
		}

		data := make([]byte, subFileLength)
		f.Read(data)
		nodes = append(nodes, Node{Group: num[0], Item: num[1], Data: data})
		subHeaderOffset = nextSubHeaderOffset
	}
	f.Close()

	nodes = append(nodes, Node{Group: targetGroup, Item: targetItem, Data: audioData})

	out, err := os.Create(sndPath)
	if err != nil { return err }
	defer out.Close()

	out.Write([]byte("ElecbyteSnd\x00"))
	binary.Write(out, binary.LittleEndian, ver)
	binary.Write(out, binary.LittleEndian, ver2)
	binary.Write(out, binary.LittleEndian, uint32(len(nodes)))
	if len(nodes) > 0 { binary.Write(out, binary.LittleEndian, uint32(24)) } else { binary.Write(out, binary.LittleEndian, uint32(0)) }

	currentOffset := uint32(24)
	for i, n := range nodes {
		nextOffset := uint32(0)
		if i < len(nodes)-1 { nextOffset = currentOffset + 16 + uint32(len(n.Data)) }
		out.Seek(int64(currentOffset), io.SeekStart)
		binary.Write(out, binary.LittleEndian, nextOffset)
		binary.Write(out, binary.LittleEndian, uint32(len(n.Data)))
		binary.Write(out, binary.LittleEndian, n.Group)
		binary.Write(out, binary.LittleEndian, n.Item)
		out.Write(n.Data)
		currentOffset = nextOffset
	}
	return nil
}

func DeleteAudio(sndPath string, targetGroup int32, targetItem int32) error {
	f, err := os.Open(sndPath)
	if err != nil { return fmt.Errorf("无法打开SND文件: %v", err) }

	buf := make([]byte, 12)
	f.Read(buf)
	if string(buf) != "ElecbyteSnd\x00" { f.Close(); return fmt.Errorf("非法的 SND 格式") }

	var ver, ver2 uint16
	var numberOfSounds, subHeaderOffset uint32
	binary.Read(f, binary.LittleEndian, &ver)
	binary.Read(f, binary.LittleEndian, &ver2)
	binary.Read(f, binary.LittleEndian, &numberOfSounds)
	binary.Read(f, binary.LittleEndian, &subHeaderOffset)

	type Node struct { Group, Item int32; Data []byte }
	var nodes []Node
	found := false

	for i := uint32(0); i < numberOfSounds; i++ {
		if subHeaderOffset == 0 { break }
		f.Seek(int64(subHeaderOffset), io.SeekStart)
		var nextSubHeaderOffset, subFileLength uint32
		var num [2]int32
		binary.Read(f, binary.LittleEndian, &nextSubHeaderOffset)
		binary.Read(f, binary.LittleEndian, &subFileLength)
		binary.Read(f, binary.LittleEndian, &num)

		data := make([]byte, subFileLength)
		f.Read(data)
		
		if num[0] == targetGroup && num[1] == targetItem {
			found = true
		} else {
			nodes = append(nodes, Node{Group: num[0], Item: num[1], Data: data})
		}
		subHeaderOffset = nextSubHeaderOffset
	}
	f.Close()

	if !found { return fmt.Errorf("未找到对应的音频编号") }

	out, err := os.Create(sndPath)
	if err != nil { return err }
	defer out.Close()

	out.Write([]byte("ElecbyteSnd\x00"))
	binary.Write(out, binary.LittleEndian, ver)
	binary.Write(out, binary.LittleEndian, ver2)
	binary.Write(out, binary.LittleEndian, uint32(len(nodes)))
	if len(nodes) > 0 { binary.Write(out, binary.LittleEndian, uint32(24)) } else { binary.Write(out, binary.LittleEndian, uint32(0)) }

	currentOffset := uint32(24)
	for i, n := range nodes {
		nextOffset := uint32(0)
		if i < len(nodes)-1 { nextOffset = currentOffset + 16 + uint32(len(n.Data)) }
		out.Seek(int64(currentOffset), io.SeekStart)
		binary.Write(out, binary.LittleEndian, nextOffset)
		binary.Write(out, binary.LittleEndian, uint32(len(n.Data)))
		binary.Write(out, binary.LittleEndian, n.Group)
		binary.Write(out, binary.LittleEndian, n.Item)
		out.Write(n.Data)
		currentOffset = nextOffset
	}
	return nil
}
