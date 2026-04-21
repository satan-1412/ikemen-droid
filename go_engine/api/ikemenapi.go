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
	"sort"

	// 导入我们的两大核心提纯车间
	"ikemenbridge/sff_module"
	"ikemenbridge/snd_module"
)

// ==========================================
// 🚀 全局 Context 内存环境 (100% 确保色表网络不断链)
// ==========================================

var activeSffContext *sff_module.Sff
var activeSffPath string

func loadActiveSff(path string) bool {
	// 如果已经是当前打开的文件，直接命中缓存
	if activeSffPath == path && activeSffContext != nil {
		return true
	}
	sff, err := sff_module.LoadSffContext(path)
	if err == nil && sff != nil {
		activeSffContext = sff
		activeSffPath = path
		return true
	}
	return false
}

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
	version, err := sff_module.ParseSffHeaderForApi(targetPath)
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
	if !loadActiveSff(sffPath) {
		return `[]`
	}
	frames := sff_module.ExtractAllFramesFromContext(activeSffContext)

	// 对无序的 map 结果进行排序输出
	sort.Slice(frames, func(i, j int) bool {
		if frames[i].Group == frames[j].Group {
			return frames[i].Item < frames[j].Item
		}
		return frames[i].Group < frames[j].Group
	})

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
	if !loadActiveSff(sffPath) {
		return nil
	}
	pngBytes, err := sff_module.RenderSpriteToPng(activeSffContext, uint16(group), uint16(item), actPath)
	if err != nil {
		return nil
	}
	return pngBytes
}

func ReplaceSffFrame(sffPath string, group int32, item int32, targetPngPath string) bool {
	err := sff_module.ReplaceFrameWithPng(sffPath, group, item, targetPngPath)
	if err == nil {
		// 替换成功后清空缓存，强制下次刷新读取新文件
		activeSffPath = ""
		activeSffContext = nil
		return true
	}
	return false
}

func GetSffPreview(sffPath string) []byte {
	if !loadActiveSff(sffPath) {
		return nil
	}
	frames := sff_module.ExtractAllFramesFromContext(activeSffContext)
	for i := 0; i < len(frames) && i < 10; i++ {
		bmp, err := sff_module.RenderSpriteToPng(activeSffContext, uint16(frames[i].Group), uint16(frames[i].Item), "")
		if err == nil && len(bmp) > 0 {
			return bmp
		}
	}
	return nil
}

// GetSffFrameExportExtension: 自动探测该素材的真实原始格式，返回 "pcx" 或 "png"
func GetSffFrameExportExtension(sffPath string, group int32, item int32) string {
	version, err := sff_module.ParseSffHeaderForApi(sffPath)
	if err == nil && len(version) > 0 && version[0] == '1' {
		return "pcx"
	}
	return "png"
}

// ExtractSffFrameRawData: 获取彻底未经过处理和渲染损耗的原始 PCX/PNG 二进制数据
func ExtractSffFrameRawData(sffPath string, group int32, item int32, actPath string) []byte {
	if !loadActiveSff(sffPath) {
		return nil
	}
	data, _, _ := sff_module.ExtractRawFrameData(activeSffContext, uint16(group), uint16(item), actPath)
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
// 🎞️ GIF 逐帧完美合成引擎
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
		disposal := byte(gif.DisposalNone)
		if i < len(g.Disposal) {
			disposal = g.Disposal[i]
		}
		if disposal == gif.DisposalPrevious {
			prevFrame = image.NewRGBA(bounds)
			draw.Draw(prevFrame, bounds, currFrame, bounds.Min, draw.Src)
		}
		if img != nil && !img.Bounds().Empty() {
			draw.Draw(currFrame, img.Bounds(), img, img.Bounds().Min, draw.Over)
		}
		newFrame := image.NewRGBA(bounds)
		draw.Draw(newFrame, bounds, currFrame, bounds.Min, draw.Src)
		frames[i] = newFrame

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

func GetGifFrameCount(gifPath string) int32 {
	if err := loadGif(gifPath); err != nil {
		return 0
	}
	return int32(len(gifCompositedFrames))
}

func DecodeGifFrame(gifPath string, index int32) []byte {
	if err := loadGif(gifPath); err != nil {
		return nil
	}
	idx := int(index)
	if idx < 0 || idx >= len(gifCompositedFrames) {
		return nil
	}
	buf := new(bytes.Buffer)
	png.Encode(buf, gifCompositedFrames[idx])
	return buf.Bytes()
}
