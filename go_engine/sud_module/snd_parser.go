package snd_module

import (
	"bytes"
	"encoding/binary"
	"fmt"
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
// 🎵 核心解包算法 (纯净剥离版，零第三方依赖)
// ==========================================

// ExtractAllNodes 遍历 SND 文件，读取所有音频的组号和序号
func ExtractAllNodes(filename string) ([]SndNodeInfo, error) {
	f, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	// 1. 读取并校验 12 字节签名
	buf := make([]byte, 12)
	n, err := f.Read(buf)
	if err != nil || string(buf[:n]) != "ElecbyteSnd\x00" {
		return nil, fmt.Errorf("invalid SND header")
	}

	// 2. 读取头部信息
	var ver, ver2 uint16
	var numberOfSounds, subHeaderOffset uint32
	binary.Read(f, binary.LittleEndian, &ver)
	binary.Read(f, binary.LittleEndian, &ver2)
	binary.Read(f, binary.LittleEndian, &numberOfSounds)
	binary.Read(f, binary.LittleEndian, &subHeaderOffset)

	nodes := make([]SndNodeInfo, 0, numberOfSounds)

	// 3. 顺藤摸瓜遍历链表
	for i := uint32(0); i < numberOfSounds; i++ {
		f.Seek(int64(subHeaderOffset), 0)
		var nextSubHeaderOffset, subFileLength uint32
		var num [2]int32
		
		binary.Read(f, binary.LittleEndian, &nextSubHeaderOffset)
		binary.Read(f, binary.LittleEndian, &subFileLength)
		binary.Read(f, binary.LittleEndian, &num)

		nodes = append(nodes, SndNodeInfo{Group: num[0], Item: num[1]})

		if nextSubHeaderOffset == 0 {
			break
		}
		subHeaderOffset = nextSubHeaderOffset
	}
	return nodes, nil
}

// ExtractWav 精准定位指定音频，直接“抠”出原生的 WAV 字节流
func ExtractWav(filename string, targetGroup int32, targetItem int32) ([]byte, error) {
	f, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	// 校验签名
	buf := make([]byte, 12)
	f.Read(buf)
	if !bytes.HasPrefix(buf, []byte("ElecbyteSnd")) {
		return nil, fmt.Errorf("invalid SND header")
	}

	// 跳过版本号，直接读数量和第一个偏移量
	f.Seek(16, 0)
	var numberOfSounds, subHeaderOffset uint32
	binary.Read(f, binary.LittleEndian, &numberOfSounds)
	binary.Read(f, binary.LittleEndian, &subHeaderOffset)

	// 遍历链表寻找目标
	for i := uint32(0); i < numberOfSounds; i++ {
		f.Seek(int64(subHeaderOffset), 0)
		var nextSubHeaderOffset, subFileLength uint32
		var num [2]int32
		
		binary.Read(f, binary.LittleEndian, &nextSubHeaderOffset)
		binary.Read(f, binary.LittleEndian, &subFileLength)
		binary.Read(f, binary.LittleEndian, &num)

		// 🎯 找到了！直接提取纯净字节
		if num[0] == targetGroup && num[1] == targetItem {
			wavData := make([]byte, subFileLength)
			f.Read(wavData)
			return wavData, nil
		}

		if nextSubHeaderOffset == 0 {
			break
		}
		subHeaderOffset = nextSubHeaderOffset
	}
	
	return nil, fmt.Errorf("sound %d,%d not found", targetGroup, targetItem)
}
