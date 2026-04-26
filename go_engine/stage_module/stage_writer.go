package stage_module

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// ==========================================
// 🗺️ 地图工程底层写入引擎 (JSON -> DEF 动态转换器)
// ==========================================

// StageData 映射了地图的所有核心机制，支持 2D 图层和 3D 模型
type StageData struct {
	Info       map[string]interface{}   `json:"Info"`
	Camera     map[string]interface{}   `json:"Camera"`
	PlayerInfo map[string]interface{}   `json:"PlayerInfo"`
	Bound      map[string]interface{}   `json:"Bound"`
	StageInfo  map[string]interface{}   `json:"StageInfo"`
	Shadow     map[string]interface{}   `json:"Shadow"`
	Reflection map[string]interface{}   `json:"Reflection"`
	Music      map[string]interface{}   `json:"Music"`
	BGDef      map[string]interface{}   `json:"BGdef"`
	Model      map[string]interface{}   `json:"Model"`     // 真3D地图引擎核心参数
	BGCtrlDef  map[string]interface{}   `json:"BGCtrlDef"` // 动态演出时间轴总控
	BGs        []map[string]interface{} `json:"BGs"`       // 多图层列阵
	BGCtrls    []map[string]interface{} `json:"BGCtrls"`   // 多时间轴动态控制器
}

func WriteStageDef(exportDir string, jsonData string) (string, error) {
	var def StageData
	err := json.Unmarshal([]byte(jsonData), &def)
	if err != nil {
		return "", fmt.Errorf("解析地图 JSON 数据失败: %v", err)
	}

	var buf bytes.Buffer

	// 核心写入器：将动态 Map 格式化为引擎标准的 INI 语法段落
	writeSection := func(title string, data map[string]interface{}) {
		if data == nil || len(data) == 0 {
			return
		}
		buf.WriteString(fmt.Sprintf("[%s]\n", title))
		for k, v := range data {
			buf.WriteString(fmt.Sprintf("%s = %v\n", k, v))
		}
		buf.WriteString("\n")
	}

	buf.WriteString("; ==========================================\n")
	buf.WriteString("; 🗺️ 由 Ikemen GO 移动端可视化地图编辑器自动生成\n")
	buf.WriteString("; ==========================================\n\n")

	// 顺序写入基础与系统控制模块
	writeSection("Info", def.Info)
	writeSection("Camera", def.Camera)
	writeSection("PlayerInfo", def.PlayerInfo)
	writeSection("Bound", def.Bound)
	writeSection("StageInfo", def.StageInfo)
	writeSection("Shadow", def.Shadow)
	writeSection("Reflection", def.Reflection)
	writeSection("Music", def.Music)
	writeSection("BGdef", def.BGDef)
	writeSection("Model", def.Model) 
	writeSection("BGCtrlDef", def.BGCtrlDef)

	// 循环写入所有图层 (处理 BG 1, BG 2 等独立图层，防误触锁定后导出的图层数据)
	for i, bg := range def.BGs {
		name, ok := bg["_name"]
		if !ok {
			name = fmt.Sprintf("%d", i)
		}
		buf.WriteString(fmt.Sprintf("[BG %v]\n", name))
		for k, v := range bg {
			if k == "_name" {
				continue
			}
			buf.WriteString(fmt.Sprintf("%s = %v\n", k, v))
		}
		buf.WriteString("\n")
	}

	// 循环写入所有控制器 (处理岩浆涨落、时间轴触发等动态演出)
	for _, ctrl := range def.BGCtrls {
		name, ok := ctrl["_name"]
		if !ok {
			name = "Ctrl"
		}
		buf.WriteString(fmt.Sprintf("[BGCtrl %v]\n", name))
		for k, v := range ctrl {
			if k == "_name" {
				continue
			}
			buf.WriteString(fmt.Sprintf("%s = %v\n", k, v))
		}
		buf.WriteString("\n")
	}

	// 智能命名与路径机制
	fileName := "NewStage.def"
	if nameVal, ok := def.Info["name"]; ok {
		fileName = strings.ReplaceAll(fmt.Sprintf("%v", nameVal), "\"", "") + ".def"
	}

	if err := os.MkdirAll(exportDir, 0755); err != nil {
		return "", err
	}

	outPath := filepath.Join(exportDir, fileName)
	err = os.WriteFile(outPath, buf.Bytes(), 0644)
	if err != nil {
		return "", err
	}

	return outPath, nil
}
