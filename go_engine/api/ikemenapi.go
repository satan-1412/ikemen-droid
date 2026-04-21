package api

import (
	"bytes"
	"encoding/json"
	"image"
	"image/draw"
	"image/gif"
	"image/png"
	"os"
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
// 🖼️ SFF 图像解析总接口 (已接入 ACT 色表通道)
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

func DecodeSffFrame(sffPath string, group int32, item int32, actPath string) []byte {
	pngBytes, err := sff_module.ExtractFrameAsPng(sffPath, group, item, actPath)
	if err != nil {
		return nil
	}
	return pngBytes
}

func ReplaceSffFrame(sffPath string, group int32, item int32, targetPngPath string) bool {
	err := sff_module.ReplaceFrameWithPng(sffPath, group, item, targetPngPath)
	return err == nil
}

func GetSffPreview(sffPath string) []byte {
	frames, err := sff_module.ExtractAllFrames(sffPath)
	if err != nil || len(frames) == 0 {
		return nil
	}
	for i := 0; i < len(frames) && i < 10; i++ {
		// 预览时不挂载 ACT，强制提取内部色表进行快速试错
		bmp, err := sff_module.ExtractFrameAsPng(sffPath, frames[i].Group, frames[i].Item, "")
		if err == nil && len(bmp) > 0 {
			return bmp
		}
	}
	return nil
}

// ==========================================
// 🚀 全新加入：无损原生导出接口 (供 Java 层识别调用)
// ==========================================

// GetSffFrameExportExtension: 自动探测该素材的真实原始格式，返回 "pcx" 或 "png"
func GetSffFrameExportExtension(sffPath string, group int32, item int32) string {
	version, err := sff_module.ParseSffHeader(sffPath)
	if err == nil && len(version) > 0 && version[0] == '1' {
		return "pcx"
	}
	return "png"
}

// ExtractSffFrameRawData: 获取彻底未经过处理和渲染损耗的原始 PCX/PNG 二进制数据
func ExtractSffFrameRawData(sffPath string, group int32, item int32, actPath string) []byte {
	data, _, _ := sff_module.ExtractRawFrameData(sffPath, group, item, actPath)
	return data
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

// ==========================================
// 🎞️ 全新加入：纯 Go 语言级 GIF 逐帧完美合成引擎
// ==========================================

var gifCachePath string
var gifCompositedFrames []*image.RGBA

func loadGif(path string) error {
	if gifCachePath == path && gifCompositedFrames != nil {
		return nil
	}
	f, err := os.Open(path)
	if err != nil {
		return err
	}
	defer f.Close()
	g, err := gif.DecodeAll(f)
	if err != nil {
		return err
	}

	bounds := g.Image[0].Bounds()
	frames := make([]*image.RGBA, len(g.Image))
	currFrame := image.NewRGBA(bounds)

	for i, img := range g.Image {
		var prevFrame *image.RGBA

		// 🔥 核心防爆盾：严密防止 Disposal 数组长度不足造成的越界 Panic (这也是闪退的罪魁祸首)
		disposal := gif.DisposalNone
		if i < len(g.Disposal) {
			disposal = g.Disposal[i]
		}

		if disposal == gif.DisposalPrevious {
			prevFrame = image.NewRGBA(bounds)
			draw.Draw(prevFrame, bounds, currFrame, bounds.Min, draw.Src)
		}

		// 🔥 防御：防止空帧、破损帧引发无效绘制
		if img != nil && !img.Bounds().Empty() {
			draw.Draw(currFrame, img.Bounds(), img, img.Bounds().Min, draw.Over)
		}

		// 独立存盘这一帧的纯净画面
		newFrame := image.NewRGBA(bounds)
		draw.Draw(newFrame, bounds, currFrame, bounds.Min, draw.Src)
		frames[i] = newFrame

		// 绘制结束后，为下一帧做画布清理准备
		if disposal == gif.DisposalBackground {
			draw.Draw(currFrame, img.Bounds(), image.Transparent, image.Point{}, draw.Src)
		} else if disposal == gif.DisposalPrevious && prevFrame != nil {
			draw.Draw(currFrame, bounds, prevFrame, bounds.Min, draw.Src)
		}
	}
	gifCachePath = path
	gifCompositedFrames = frames
	return nil
}

// 核心修复1：明确返回 int32 类型，对应 Java 的 int
func GetGifFrameCount(gifPath string) int32 {
	if err := loadGif(gifPath); err != nil {
		return 0
	}
	return int32(len(gifCompositedFrames))
}

// 核心修复2：明确接收 int32 类型参数，防止 Java 传参类型错位
func DecodeGifFrame(gifPath string, index int32) []byte {
	if err := loadGif(gifPath); err != nil {
		return nil
	}
	idx := int(index) // 转回 Go 内部使用的原生 int
	if idx < 0 || idx >= len(gifCompositedFrames) {
		return nil
	}
	buf := new(bytes.Buffer)
	png.Encode(buf, gifCompositedFrames[idx])
	return buf.Bytes()
}

