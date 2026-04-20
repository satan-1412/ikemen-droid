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
	if err != nil {
		return nil, err
	}

	if string(buf[:n]) != "ElecbyteSnd\x00" {
		return nil, fmt.Errorf("invalid SND header")
	}

	// 2. 读取头部信息
	var ver uint16
	var ver2 uint16
	var numberOfSounds uint32
	var subHeaderOffset uint32

	binary.Read(f, binary.LittleEndian, &ver)
	binary.Read(f, binary.LittleEndian, &ver2)
	binary.Read(f, binary.LittleEndian, &numberOfSounds)
	binary.Read(f, binary.LittleEndian, &subHeaderOffset)

	nodes := make([]SndNodeInfo, 0, numberOfSounds)

	// 3. 顺藤摸瓜遍历链表
	for i := uint32(0); i < numberOfSounds; i++ {
		f.Seek(int64(subHeaderOffset), 0)
		var nextSubHeaderOffset uint32
		var subFileLength uint32
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
	var numberOfSounds uint32
	var subHeaderOffset uint32

	binary.Read(f, binary.LittleEndian, &numberOfSounds)
	binary.Read(f, binary.LittleEndian, &subHeaderOffset)

	// 遍历链表寻找目标
	for i := uint32(0); i < numberOfSounds; i++ {
		f.Seek(int64(subHeaderOffset), 0)
		var nextSubHeaderOffset uint32
		var subFileLength uint32
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

// ==========================================
// 🛠️ 独家底层写入机制：真正实现 SND 音频替换
// ==========================================

// ReplaceAudioWithWav 采用“安全追加+指针重定向”的链表算法，完美无损替换任意大小的音频
func ReplaceAudioWithWav(sndPath string, targetGroup int32, targetItem int32, wavPath string) error {
	// 1. 读取新的 WAV 音频文件
	wavData, err := os.ReadFile(wavPath)
	if err != nil {
		return fmt.Errorf("无法读取目标WAV文件: %v", err)
	}

	// 2. 以【读写追加模式】打开目标 SND 文件
	f, err := os.OpenFile(sndPath, os.O_RDWR, 0644)
	if err != nil {
		return fmt.Errorf("无法打开SND文件: %v", err)
	}
	defer f.Close()

	// 3. 校验签名
	buf := make([]byte, 12)
	f.Read(buf)
	if string(buf) != "ElecbyteSnd\x00" {
		return fmt.Errorf("非法的 SND 格式")
	}

	// 读取初始头部信息，并定位“当前指针的位置”
	f.Seek(16, io.SeekStart)
	var numberOfSounds uint32
	var subHeaderOffset uint32
	binary.Read(f, binary.LittleEndian, &numberOfSounds)
	binary.Read(f, binary.LittleEndian, &subHeaderOffset)

	// 记录指向上一个节点（或根节点）的指针偏移量
	// 初始值 20 是因为 main header 的 subHeaderOffset 存在偏移量为 20 的位置 (12+2+2+4)
	var ptrToCurrentNode int64 = 20

	// 4. 遍历链表寻找替换目标
	for i := uint32(0); i < numberOfSounds; i++ {
		if subHeaderOffset == 0 {
			break
		}

		f.Seek(int64(subHeaderOffset), io.SeekStart)
		var nextSubHeaderOffset uint32
		var subFileLength uint32
		var num [2]int32

		binary.Read(f, binary.LittleEndian, &nextSubHeaderOffset)
		binary.Read(f, binary.LittleEndian, &subFileLength)
		binary.Read(f, binary.LittleEndian, &num)

		// 🎯 找到目标音频，实施追加替换法
		if num[0] == targetGroup && num[1] == targetItem {
			// A. 计算文件末尾的偏移量
			fileInfo, _ := f.Stat()
			appendOffset := fileInfo.Size()

			// B. 移动到文件末尾，构造新的节点头部并写入
			f.Seek(0, io.SeekEnd)
			binary.Write(f, binary.LittleEndian, nextSubHeaderOffset) // 继承原有的下一个指针
			binary.Write(f, binary.LittleEndian, uint32(len(wavData))) // 新音频的真实长度
			binary.Write(f, binary.LittleEndian, num[0])               // 原组号
			binary.Write(f, binary.LittleEndian, num[1])               // 原序号

			// 写入真正的音频字节流
			_, err = f.Write(wavData)
			if err != nil {
				return fmt.Errorf("写入新音频数据失败: %v", err)
			}

			// C. 回溯到上一首音频，修改它的指针，让链表指向我们刚刚追加在文件末尾的新音频！
			f.Seek(ptrToCurrentNode, io.SeekStart)
			binary.Write(f, binary.LittleEndian, uint32(appendOffset))

			return nil // 替换圆满成功
		}

		// 迭代游标：下一首歌的指针位置，恰好就是当前首歌头部的首个 4 字节
		ptrToCurrentNode = int64(subHeaderOffset)
		subHeaderOffset = nextSubHeaderOffset
	}

	return fmt.Errorf("在 SND 中未找到 Group:%d Item:%d", targetGroup, targetItem)
}
