package snd_module

import (
	"bytes"
	"encoding/binary"
	"fmt"
	"io"
	"os"
)

// ==========================================
// 🛠️ 基础结构定义
// ==========================================

type SndNodeInfo struct {
	Group int32
	Item  int32
}

// ==========================================
// 🎵 核心解包算法 (纯净剥离版，完全展开)
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
// 🛠️ 独家底层写入机制：真正实现 SND 音频替换 (顺序重建防静音)
// ==========================================

func ReplaceAudioWithWav(sndPath string, targetGroup int32, targetItem int32, audioPath string) error {
	audioData, err := os.ReadFile(audioPath)
	if err != nil {
		return fmt.Errorf("无法读取音频源文件: %v", err)
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

		if num[0] == targetGroup && num[1] == targetItem {
			data = audioData
		}
		nodes = append(nodes, Node{Group: num[0], Item: num[1], Data: data})
		subHeaderOffset = nextSubHeaderOffset
	}
	f.Close()

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
