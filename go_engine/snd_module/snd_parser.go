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
		f.Seek(int64(subHeaderOffset), 0)
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

	f.Seek(16, 0)
	var numberOfSounds, subHeaderOffset uint32
	binary.Read(f, binary.LittleEndian, &numberOfSounds)
	binary.Read(f, binary.LittleEndian, &subHeaderOffset)

	for i := uint32(0); i < numberOfSounds; i++ {
		if subHeaderOffset == 0 {
			break
		}
		f.Seek(int64(subHeaderOffset), 0)
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
// 🛠️ 独家底层写入机制：真正实现 SND 音频替换
// ==========================================

func ReplaceAudioWithWav(sndPath string, targetGroup int32, targetItem int32, wavPath string) error {
	wavData, err := os.ReadFile(wavPath)
	if err != nil {
		return fmt.Errorf("无法读取目标音频文件: %v", err)
	}

	// 基础检测：简单拦截非 WAV 格式 (保护原生播放器不崩溃)
	if len(wavData) < 4 || string(wavData[:4]) != "RIFF" {
		return fmt.Errorf("目前底层引擎仅严格支持原生 PCM WAV 格式替换，请转换格式")
	}

	f, err := os.OpenFile(sndPath, os.O_RDWR, 0644)
	if err != nil {
		return fmt.Errorf("无法打开SND文件: %v", err)
	}
	defer f.Close()

	buf := make([]byte, 12)
	f.Read(buf)
	if string(buf) != "ElecbyteSnd\x00" {
		return fmt.Errorf("非法的 SND 格式")
	}

	f.Seek(16, io.SeekStart)
	var numberOfSounds, subHeaderOffset uint32
	binary.Read(f, binary.LittleEndian, &numberOfSounds)
	binary.Read(f, binary.LittleEndian, &subHeaderOffset)

	var ptrToCurrentNode int64 = 20

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
			fileInfo, _ := f.Stat()
			appendOffset := fileInfo.Size()

			f.Seek(0, io.SeekEnd)
			binary.Write(f, binary.LittleEndian, nextSubHeaderOffset) 
			binary.Write(f, binary.LittleEndian, uint32(len(wavData))) 
			binary.Write(f, binary.LittleEndian, num[0])               
			binary.Write(f, binary.LittleEndian, num[1])               

			_, err = f.Write(wavData)
			if err != nil {
				return fmt.Errorf("写入新音频数据失败: %v", err)
			}

			f.Seek(ptrToCurrentNode, io.SeekStart)
			binary.Write(f, binary.LittleEndian, uint32(appendOffset))

			return nil 
		}
		ptrToCurrentNode = int64(subHeaderOffset)
		subHeaderOffset = nextSubHeaderOffset
	}

	return fmt.Errorf("在 SND 中未找到 Group:%d Item:%d", targetGroup, targetItem)
}
