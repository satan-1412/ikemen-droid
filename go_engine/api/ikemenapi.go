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

	"ikemenbridge/sff_module"
	"ikemenbridge/snd_module"
	"ikemenbridge/stage_module"
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

func ExportSffFrameNative(sffPath string, group int32, item int32, actPath string, outDir string) string {
	savedPath, err := sff_module.ExportFrameNative(sffPath, group, item, actPath, outDir)
	if err != nil {
		return ""
	}
	return savedPath
}

func GetSffPreview(sffPath string) []byte {
	frames, err := sff_module.ExtractAllFrames(sffPath)
	if err != nil || len(frames) == 0 {
		return nil
	}
	for i := 0; i < len(frames) && i < 10; i++ {
		bmp, err := sff_module.ExtractFrameAsPng(sffPath, frames[i].Group, frames[i].Item, "")
		if err == nil && len(bmp) > 0 {
			return bmp
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

// ==========================================
// 🎞️ GIF 极限省内存拆解引擎 (完美适配 32 位系统)
// ==========================================

var (
	activeGifPath string
	activeGif     *gif.GIF
)

// GetGifFrameCount 读取文件结构，极低内存消耗，仅缓存调色板数据
func GetGifFrameCount(gifPath string) int32 {
	fileInfo, err := os.Stat(gifPath)
	if err != nil || fileInfo.IsDir() {
		return 0
	}

	if activeGifPath == gifPath && activeGif != nil {
		return int32(len(activeGif.Image))
	}

	f, err := os.Open(gifPath)
	if err != nil {
		return 0
	}
	defer f.Close()

	g, err := gif.DecodeAll(f)
	if err != nil || len(g.Image) == 0 {
		return 0
	}

	activeGifPath = gifPath
	activeGif = g
	return int32(len(g.Image))
}

// DecodeGifFrame 即时合成请求的帧，告别数组堆叠，永不 OOM
func DecodeGifFrame(gifPath string, index int32) []byte {
	if GetGifFrameCount(gifPath) == 0 {
		return nil
	}

	idx := int(index)
	if idx < 0 || idx >= len(activeGif.Image) {
		return nil
	}

	// 取 GIF 逻辑画布尺寸
	bounds := image.Rect(0, 0, activeGif.Config.Width, activeGif.Config.Height)
	if bounds.Dx() == 0 || bounds.Dy() == 0 {
		bounds = activeGif.Image[0].Bounds()
	}

	// 仅分配一张画板和一张备用画板的内存 (最大只需几 MB)
	canvas := image.NewRGBA(bounds)
	var backup *image.RGBA

	for i := 0; i <= idx; i++ {
		img := activeGif.Image[i]
		disposal := byte(gif.DisposalNone)
		if i < len(activeGif.Disposal) {
			disposal = activeGif.Disposal[i]
		}

		if disposal == gif.DisposalPrevious {
			if backup == nil {
				backup = image.NewRGBA(bounds)
			}
			copy(backup.Pix, canvas.Pix) // 快速内存克隆，保存残影
		}

		// 将当前帧覆盖在画板上
		draw.Draw(canvas, img.Bounds(), img, img.Bounds().Min, draw.Over)

		// 如果这就是我们要的帧，直接截断循环，返回结果
		if i == idx {
			break
		}

		// 根据 Disposal 规则，为下一帧清理画板
		if disposal == gif.DisposalBackground {
			draw.Draw(canvas, img.Bounds(), image.Transparent, image.Point{}, draw.Src)
		} else if disposal == gif.DisposalPrevious && backup != nil {
			copy(canvas.Pix, backup.Pix) // 恢复残影
		}
	}

	buf := new(bytes.Buffer)
	err := png.Encode(buf, canvas)
	if err != nil {
		return nil
	}
	return buf.Bytes()
}

// GetGifPreview 用于列表界面极速提取第一帧作为预览图，不执行完整结构解码
func GetGifPreview(gifPath string) []byte {
	fileInfo, err := os.Stat(gifPath)
	if err != nil || fileInfo.IsDir() {
		return nil
	}

	f, err := os.Open(gifPath)
	if err != nil {
		return nil
	}
	defer f.Close()

	img, err := gif.Decode(f)
	if err != nil {
		return nil
	}

	buf := new(bytes.Buffer)
	err = png.Encode(buf, img)
	if err != nil {
		return nil
	}
	return buf.Bytes()
}

// ==========================================
// 🎨 色表注入与提取 JNI 接口
// ==========================================

func ExtractSffPalette(sffPath string, actPath string) bool {
	err := sff_module.ExtractInternalPalette(sffPath, actPath)
	return err == nil
}

func InjectSffPalette(sffPath string, actPath string) bool {
	err := sff_module.InjectInternalPalette(sffPath, actPath)
	return err == nil
}

// ==========================================
// 🗺️ 地图编辑器：打包生成 DEF 核心工程
// ==========================================

// ExportStageDef 接收安卓 UI 传来的 JSON 和保存路径，生成完美的 .def 配置文件
func ExportStageDef(exportDir string, stageJson string) string {
	outPath, err := stage_module.WriteStageDef(exportDir, stageJson)
	if err != nil {
		// 遭遇致命错误时返回空字符串通知 Java 弹窗报错
		return "" 
	}
	return outPath
}
