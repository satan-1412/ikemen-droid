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
	X      int16 `json:"x"` 
	Y      int16 `json:"y"` 
}

type SndNode struct {
	Group int32 `json:"group"`
	Item  int32 `json:"item"`
}

// ==========================================
// 🖼️ SFF 图像解析总接口
// ==========================================

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
			X:      f.X, 
			Y:      f.Y,
		}
	}

	jsonBytes, _ := json.Marshal(outFrames)
	return string(jsonBytes)
}

func DecodeSffFrame(sffPath string, group int32, item int32) []byte {
	pngBytes, err := sff_module.ExtractFrameAsPng(sffPath, group, item)
	if err != nil {
		return nil
	}
	return pngBytes
}

func ReplaceSffFrame(sffPath string, group int32, item int32, targetPngPath string) bool {
	err := sff_module.ReplaceFrameWithPng(sffPath, group, item, targetPngPath)
	return err == nil
}

// ==========================================
// 🎯 新增：SFF 智能预览拉取机制 (为前端卡片自动供图)
// ==========================================

// GetSffPreview 循环提取 SFF 内部有效帧，抓取首个存活图象用作预览
func GetSffPreview(sffPath string) []byte {
	frames, err := sff_module.ExtractAllFrames(sffPath)
	if err != nil || len(frames) == 0 {
		return nil
	}

	// 轮询试错法 (最多抓取前10帧以防纯空白帧开局)
	for i := 0; i < len(frames) && i < 10; i++ {
		bmp, err := sff_module.ExtractFrameAsPng(sffPath, frames[i].Group, frames[i].Item)
		if err == nil && len(bmp) > 0 {
			return bmp // 抓到了，立即返回 PNG 流
		}
	}
	return nil
}

// ==========================================
// 🎵 SND 音频解析总接口
// ==========================================

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

func ExtractSndAudio(sndPath string, group int32, item int32) []byte {
	wavBytes, err := snd_module.ExtractWav(sndPath, group, item)
	if err != nil {
		return nil
	}
	return wavBytes
}

func ReplaceSndAudio(sndPath string, group int32, item int32, targetWavPath string) bool {
	err := snd_module.ReplaceAudioWithWav(sndPath, group, item, targetWavPath)
	return err == nil
}
