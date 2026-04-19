package api

import (
	"encoding/json"
	"path/filepath"

	// 导入我们的两大核心提纯车间
	"ikemenbridge/sff_module"
	"ikemenbridge/snd_module"
)

// ==========================================
// 📦 内部数据结构 (用于转为 JSON 传给 Java)
// ==========================================

type SffInfo struct {
	Name     string `json:"name"`
	FilePath string `json:"filePath"`
	Version  string `json:"version"`
}

type SffFrame struct {
	Group  int32 `json:"group"`
	Item   int32 `json:"item"`
	Width  int32 `json:"width"`
	Height int32 `json:"height"`
}

type SndNode struct {
	Group int32 `json:"group"`
	Item  int32 `json:"item"`
}

// ==========================================
// 🖼️ SFF 图像解析总接口
// ==========================================

// ScanSff 扫描并验证 SFF 文件头部签名
func ScanSff(targetPath string) string {
	version, err := sff_module.ParseSffHeader(targetPath)
	if err != nil {
		return `[]` 
	}

	info := SffInfo{
		Name:     filepath.Base(targetPath),
		FilePath: targetPath,
		Version:  "SFF v" + version,
	}

	result := []SffInfo{info}
	jsonBytes, _ := json.Marshal(result)
	return string(jsonBytes)
}

// GetAllFrames 获取SFF内所有动作组/帧的结构
func GetAllFrames(sffPath string) string {
	frames, err := sff_module.ExtractAllFrames(sffPath)
	if err != nil || len(frames) == 0 {
		return `[]`
	}

	outFrames := make([]SffFrame, len(frames))
	for i, f := range frames {
		outFrames[i] = SffFrame{
			Group:  f.Group,
			Item:   f.Item,
			Width:  f.Width,
			Height: f.Height,
		}
	}

	jsonBytes, _ := json.Marshal(outFrames)
	return string(jsonBytes)
}

// DecodeSffFrame 将指定帧解码为纯净的 PNG 图片字节流
func DecodeSffFrame(sffPath string, group int32, item int32) []byte {
	pngBytes, err := sff_module.ExtractFrameAsPng(sffPath, group, item)
	if err != nil {
		return nil
	}
	return pngBytes
}

// ==========================================
// 🎵 SND 音频解析总接口
// ==========================================

// ScanSnd 扫描 SND 文件并提取所有音频链表
func ScanSnd(sndPath string) string {
	nodes, err := snd_module.ExtractAllNodes(sndPath)
	if err != nil || len(nodes) == 0 {
		return `[]`
	}
	
	outNodes := make([]SndNode, len(nodes))
	for i, n := range nodes {
		outNodes[i] = SndNode{Group: n.Group, Item: n.Item}
	}
	
	jsonBytes, _ := json.Marshal(outNodes)
	return string(jsonBytes)
}

// ExtractSndAudio 提取指定音频的真实 WAV 字节流供 Java 原生播放
func ExtractSndAudio(sndPath string, group int32, item int32) []byte {
	wavBytes, err := snd_module.ExtractWav(sndPath, group, item)
	if err != nil {
		return nil
	}
	return wavBytes
}
