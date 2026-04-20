package api

import (
	"encoding/json"
	"path/filepath"

	"ikemenbridge/sff_module"
	"ikemenbridge/snd_module"
)

type SffInfo struct {
	Name       string `json:"name"`
	FilePath   string `json:"filePath"`
	Version    string `json:"version"`
	FirstGroup int32  `json:"firstGroup"`
	FirstItem  int32  `json:"firstItem"`
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

func ScanSff(targetPath string) string {
	version, err := sff_module.ParseSffHeader(targetPath)
	if err != nil {
		return `[]`
	}

	info := SffInfo{
		Name:       filepath.Base(targetPath),
		FilePath:   targetPath,
		Version:    "SFF v" + version,
		FirstGroup: 0,
		FirstItem:  0,
	}

	frames, _ := sff_module.ExtractAllFrames(targetPath)
	if len(frames) > 0 {
		info.FirstGroup = frames[0].Group
		info.FirstItem = frames[0].Item
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
		outFrames[i] = SffFrame{Group: f.Group, Item: f.Item, Width: f.Width, Height: f.Height, X: f.X, Y: f.Y}
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
