package snd_module

import (
	"bytes"
	"encoding/binary"
	"fmt"
	"io"
	"os"
)

type SndNodeInfo struct {
	Group int32
	Item  int32
}

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

		if nextSubHeaderOffset == 0 {
			break
		}
		subHeaderOffset = nextSubHeaderOffset
	}

	return nil, fmt.Errorf("sound %d,%d not found", targetGroup, targetItem)
}

func ReplaceAudioWithWav(sndPath string, targetGroup int32, targetItem int32, wavPath string) error {
	wavData, err := os.ReadFile(wavPath)
	if err != nil {
		return fmt.Errorf("无法读取目标WAV文件: %v", err)
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
