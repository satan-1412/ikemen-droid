package sff_module

import (
	"bytes"
	"encoding/binary"
	"fmt"
	"image"
	"image/color"
	"image/png"
	"io"
	"os"
)

// ==========================================
// 🛠️ 基础结构定义
// ==========================================

type SffHeader struct {
	Version                  [4]byte
	FirstSpriteHeaderOffset  uint32
	FirstPaletteHeaderOffset uint32
	NumberOfSprites          uint32
	NumberOfPalettes         uint32
}

func (sh *SffHeader) Read(r io.Reader, lofs *uint32, tofs *uint32) error {
	buf := make([]byte, 12)
	n, err := r.Read(buf)
	if err != nil {
		return err
	}
	if string(buf[:n]) != "ElecbyteSpr\x00" {
		return fmt.Errorf("Unrecognized SFF file, invalid header")
	}

	read := func(x interface{}) error {
		return binary.Read(r, binary.LittleEndian, x)
	}

	read(&sh.Version[3])
	read(&sh.Version[2])
	read(&sh.Version[1])
	read(&sh.Version[0])

	var dummy uint32
	read(&dummy)
	_ = dummy // 消除 Go 编译器的未使用警告

	switch sh.Version[0] {
	case 1:
		sh.FirstPaletteHeaderOffset, sh.NumberOfPalettes = 0, 0
		read(&sh.NumberOfSprites)
		read(&sh.FirstSpriteHeaderOffset)
		read(&dummy)
		_ = dummy
	case 2:
		for i := 0; i < 4; i++ {
			read(&dummy)
			_ = dummy
		}
		read(&sh.FirstSpriteHeaderOffset)
		read(&sh.NumberOfSprites)
		read(&sh.FirstPaletteHeaderOffset)
		read(&sh.NumberOfPalettes)
		read(lofs)
		read(&dummy)
		_ = dummy
		read(tofs)
	default:
		return fmt.Errorf("Unrecognized SFF version")
	}
	return nil
}

type PaletteList struct {
	palettes   [][]uint32
	paletteMap []int
	PalTable   map[[2]uint16]int
}

func (pl *PaletteList) init() {
	pl.palettes = nil
	pl.paletteMap = nil
	pl.PalTable = make(map[[2]uint16]int)
}

func (pl *PaletteList) SetSource(i int, p []uint32) {
	if i < 0 {
		return
	}
	for len(pl.palettes) <= i {
		pl.palettes = append(pl.palettes, nil)
	}
	for len(pl.paletteMap) <= i {
		pl.paletteMap = append(pl.paletteMap, len(pl.paletteMap))
	}
	pl.palettes[i] = p
	pl.paletteMap[i] = i
}

func (pl *PaletteList) NewPal() (i int, p []uint32) {
	i, p = len(pl.palettes), make([]uint32, 256)
	pl.SetSource(i, p)
	return
}

func (pl *PaletteList) Get(i int) []uint32 {
	if len(pl.paletteMap) == 0 || i < 0 || i >= len(pl.paletteMap) {
		return nil
	}
	return pl.palettes[pl.paletteMap[i]]
}

type Sprite struct {
	Pal             []uint32
	Group           uint16
	Number          uint16
	Size            [2]uint16
	Offset          [2]int16
	IndexOfPrevious uint16 // 连体婴索引机制
	palidx          int
	rle             int
	coldepth        byte
	PxlData         []byte
	IsRaw           bool
	DataOffset      uint32
	DataSize        uint32
}

func newSprite() *Sprite {
	return &Sprite{palidx: -1}
}

// ==========================================
// 🎨 ACT 外部调色板读取机制 (备用)
// ==========================================
func ReadActPalette(filename string) ([]uint32, error) {
	f, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	pal := make([]uint32, 256)
	var rgb [3]byte
	for i := 255; i >= 0; i-- {
		if err := binary.Read(f, binary.LittleEndian, &rgb); err != nil {
			break
		}
		var alpha byte = 255
		if i == 0 {
			alpha = 0
		}
		pal[i] = uint32(alpha)<<24 | uint32(rgb[0])<<16 | uint32(rgb[1])<<8 | uint32(rgb[2])
	}
	return pal, nil
}

// ==========================================
// 🧩 核心解压算法 (直接从官方提纯，完全展开排版)
// ==========================================

func (s *Sprite) RlePcxDecode(rle []byte) (p []byte) {
	if len(rle) == 0 || s.rle <= 0 {
		return rle
	}
	p = make([]byte, int(s.Size[0])*int(s.Size[1]))
	i := 0
	j := 0
	k := 0
	w := int(s.Size[0])

	for j < len(p) {
		n := 1
		d := rle[i]
		if i < len(rle)-1 {
			i++
		}
		if d >= 0xc0 {
			n = int(d & 0x3f)
			d = rle[i]
			if i < len(rle)-1 {
				i++
			}
		}
		for ; n > 0; n-- {
			if k < w && j < len(p) {
				p[j] = d
				j++
			}
			k++
			if k == s.rle {
				k = 0
				n = 1
			}
		}
	}
	s.rle = 0
	return
}

func (s *Sprite) Rle8Decode(rle []byte) (p []byte) {
	if len(rle) == 0 {
		return rle
	}
	p = make([]byte, int(s.Size[0])*int(s.Size[1]))
	i := 0
	j := 0

	for j < len(p) {
		n := 1
		d := rle[i]
		if i < len(rle)-1 {
			i++
		}
		if d&0xc0 == 0x40 {
			n = int(d & 0x3f)
			d = rle[i]
			if i < len(rle)-1 {
				i++
			}
		}
		for ; n > 0; n-- {
			if j < len(p) {
				p[j] = d
				j++
			}
		}
	}
	return
}

func (s *Sprite) Rle5Decode(rle []byte) (p []byte) {
	if len(rle) == 0 {
		return rle
	}
	p = make([]byte, int(s.Size[0])*int(s.Size[1]))
	i := 0
	j := 0

	for j < len(p) {
		rl := int(rle[i])
		if i < len(rle)-1 {
			i++
		}
		dl := int(rle[i] & 0x7f)
		c := byte(0)
		if rle[i]>>7 != 0 {
			if i < len(rle)-1 {
				i++
			}
			c = rle[i]
		}
		if i < len(rle)-1 {
			i++
		}
		for {
			if j < len(p) {
				p[j] = c
				j++
			}
			rl--
			if rl < 0 {
				dl--
				if dl < 0 {
					break
				}
				c = rle[i] & 0x1f
				rl = int(rle[i] >> 5)
				if i < len(rle)-1 {
					i++
				}
			}
		}
	}
	return
}

func (s *Sprite) Lz5Decode(rle []byte) (p []byte) {
	if len(rle) == 0 {
		return rle
	}
	p = make([]byte, int(s.Size[0])*int(s.Size[1]))
	i := 0
	j := 0
	n := 0

	ct := rle[i]
	cts := uint(0)
	rb := byte(0)
	rbc := uint(0)

	if i < len(rle)-1 {
		i++
	}
	for j < len(p) {
		d := int(rle[i])
		if i < len(rle)-1 {
			i++
		}
		if ct&byte(1<<cts) != 0 {
			if d&0x3f == 0 {
				d = (d<<2 | int(rle[i])) + 1
				if i < len(rle)-1 {
					i++
				}
				n = int(rle[i]) + 2
				if i < len(rle)-1 {
					i++
				}
			} else {
				rb |= byte(d & 0xc0 >> rbc)
				rbc += 2
				n = int(d & 0x3f)
				if rbc < 8 {
					d = int(rle[i]) + 1
					if i < len(rle)-1 {
						i++
					}
				} else {
					d = int(rb) + 1
					rb = 0
					rbc = 0
				}
			}
			for {
				if j < len(p) {
					p[j] = p[j-d]
					j++
				}
				n--
				if n < 0 {
					break
				}
			}
		} else {
			if d&0xe0 == 0 {
				n = int(rle[i]) + 8
				if i < len(rle)-1 {
					i++
				}
			} else {
				n = d >> 5
				d &= 0x1f
			}
			for ; n > 0; n-- {
				if j < len(p) {
					p[j] = byte(d)
					j++
				}
			}
		}
		cts++
		if cts >= 8 {
			ct = rle[i]
			cts = 0
			if i < len(rle)-1 {
				i++
			}
		}
	}
	return
}

func ReadPalette(f io.ReadSeeker, offset int64, size uint32, version2 bool) ([]uint32, error) {
	if _, err := f.Seek(offset, io.SeekStart); err != nil {
		return nil, err
	}
	rawCount := int(size) / 4
	depth := 1
	for depth < rawCount {
		depth *= 2
	}
	if depth < 16 {
		depth = 16
	}
	if depth > 256 {
		depth = 256
	}
	pal := make([]uint32, depth)
	for i := 0; i < len(pal); i++ {
		var rgba [4]byte
		if i < rawCount {
			if err := binary.Read(f, binary.LittleEndian, rgba[:]); err != nil {
				return nil, err
			}
		}
		if !version2 {
			if i == 0 {
				rgba[3] = 0
			} else {
				rgba[3] = 255
			}
		}
		pal[i] = uint32(rgba[3])<<24 | uint32(rgba[2])<<16 | uint32(rgba[1])<<8 | uint32(rgba[0])
	}
	return pal, nil
}

// ==========================================
// 🔍 封装对外的读取与提取方法
// ==========================================

// ParseSffHeader 只解析头部，速度极快
func ParseSffHeader(filename string) (string, error) {
	f, err := os.Open(filename)
	if err != nil {
		return "", err
	}
	defer f.Close()

	var h SffHeader
	var lofs, tofs uint32
	if err := h.Read(f, &lofs, &tofs); err != nil {
		return "", err
	}
	versionStr := fmt.Sprintf("%d.%d%d%d", h.Version[3], h.Version[2], h.Version[1], h.Version[0])
	return versionStr, nil
}

type SffFrameInfo struct {
	Group  int32
	Item   int32
	Width  int32
	Height int32
}

// ExtractAllFrames 遍历文件并返回所有帧的基本信息
func ExtractAllFrames(filename string) ([]SffFrameInfo, error) {
	f, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	var h SffHeader
	var lofs, tofs uint32
	if err := h.Read(f, &lofs, &tofs); err != nil {
		return nil, err
	}

	frames := make([]SffFrameInfo, 0)
	read := func(x interface{}) error { 
		return binary.Read(f, binary.LittleEndian, x) 
	}

	shofs := int64(h.FirstSpriteHeaderOffset)
	for i := 0; i < int(h.NumberOfSprites); i++ {
		f.Seek(shofs, 0)
		var xofs, size uint32
		var group, number uint16
		var width, height uint16
		var dummy16 uint16

		if h.Version[0] == 1 {
			read(&xofs)
			read(&size)
			_ = size // 消除未使用警告
			f.Seek(4, 1) // 跳过 offset
			read(&group)
			read(&number)
			read(&dummy16) // link
			_ = dummy16 // 消除未使用警告
			frames = append(frames, SffFrameInfo{Group: int32(group), Item: int32(number), Width: 0, Height: 0})
			shofs = int64(xofs)
		} else {
			read(&group)
			read(&number)
			read(&width)
			read(&height)
			frames = append(frames, SffFrameInfo{Group: int32(group), Item: int32(number), Width: int32(width), Height: int32(height)})
			shofs += 28
		}
	}
	return frames, nil
}

// ExtractFrameAsPng 将指定的动作组解压并映射调色板，直接输出 PNG 字节流
func ExtractFrameAsPng(filename string, targetGroup int32, targetItem int32) ([]byte, error) {
	f, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	var h SffHeader
	var lofs, tofs uint32
	h.Read(f, &lofs, &tofs)

	read := func(x interface{}) error { 
		return binary.Read(f, binary.LittleEndian, x) 
	}

	shofs := int64(h.FirstSpriteHeaderOffset)
	pl := &PaletteList{}
	pl.init()

	sprites := make([]*Sprite, h.NumberOfSprites)

	// 第一次遍历：加载所有元数据，解决连体婴指针依赖
	for i := 0; i < int(h.NumberOfSprites); i++ {
		f.Seek(shofs, 0)
		spr := newSprite()
		var xofs uint32

		if h.Version[0] == 1 {
			read(&xofs)
			read(&spr.DataSize)
			read(&spr.Offset)
			read(&spr.Group)
			read(&spr.Number)
			read(&spr.IndexOfPrevious)
			spr.DataOffset = xofs
			shofs = int64(xofs)
		} else {
			read(&spr.Group)
			read(&spr.Number)
			read(&spr.Size)
			read(&spr.Offset)
			read(&spr.IndexOfPrevious)
			var format byte
			read(&format)
			spr.rle = -int(format)
			read(&spr.coldepth)
			read(&xofs)
			read(&spr.DataSize)
			var tmp uint16
			read(&tmp)
			spr.palidx = int(tmp)
			read(&tmp)
			if tmp&1 == 0 {
				xofs += lofs
			} else {
				xofs += tofs
			}
			spr.DataOffset = xofs
			shofs += 28
		}
		sprites[i] = spr
	}

	// 查找目标帧
	var target *Sprite
	for _, spr := range sprites {
		if int32(spr.Group) == targetGroup && int32(spr.Number) == targetItem {
			target = spr
			break
		}
	}

	if target == nil {
		return nil, fmt.Errorf("Frame not found")
	}

	// 🚨 核心修复：连体婴数据重定向
	if target.DataSize == 0 && target.IndexOfPrevious < uint16(len(sprites)) {
		sourceSpr := sprites[target.IndexOfPrevious]
		target.DataOffset = sourceSpr.DataOffset
		target.DataSize = sourceSpr.DataSize
		target.rle = sourceSpr.rle
		target.coldepth = sourceSpr.coldepth
		target.palidx = sourceSpr.palidx
		if h.Version[0] == 1 {
			target.Size = sourceSpr.Size
		}
	}

	if target.DataSize == 0 {
		return nil, fmt.Errorf("Empty linked frame data")
	}

	// 读取真实图像数据
	if h.Version[0] == 1 {
		f.Seek(int64(target.DataOffset), 0)
		var dummy uint16
		read(&dummy)
		_ = dummy // 消除未使用警告
		
		var encoding, bpp byte
		read(&encoding)
		read(&bpp)
		_ = bpp // 消除未使用警告
		
		var rect [4]uint16
		read(&rect)
		
		f.Seek(int64(target.DataOffset)+66, 0)
		var bpl uint16
		read(&bpl)
		
		target.Size[0] = rect[2] - rect[0] + 1
		target.Size[1] = rect[3] - rect[1] + 1
		if encoding == 1 {
			target.rle = int(bpl)
		} else {
			target.rle = 0
		}

		pcxDataStart := int64(target.DataOffset) + 128
		paletteOffset := int64(target.DataOffset) + int64(target.DataSize) - 769 
		rleSize := paletteOffset - pcxDataStart
		if rleSize < 0 { 
			rleSize = 0 
		}

		px := make([]byte, rleSize)
		f.Seek(pcxDataStart, 0)
		f.Read(px)

		var pal []uint32
		target.palidx, pal = pl.NewPal()
		f.Seek(paletteOffset+1, 0)
		var rgb [3]byte
		for c := range pal {
			f.Read(rgb[:])
			var alpha byte = 255
			if c == 0 { 
				alpha = 0 
			}
			pal[c] = uint32(alpha)<<24 | uint32(rgb[2])<<16 | uint32(rgb[1])<<8 | uint32(rgb[0])
		}
		target.PxlData = target.RlePcxDecode(px)
		target.Pal = pal

	} else {
		f.Seek(int64(target.DataOffset), 0)
		px := make([]uint8, target.DataSize)
		f.Read(px)

		if target.rle == 0 {
			if target.coldepth == 8 {
				target.PxlData = px
			} else {
				target.IsRaw = true
				target.PxlData = px
			}
		} else {
			format := -target.rle
			f.Seek(int64(target.DataOffset)+4, 0)
			if 2 <= format && format <= 4 {
				size := target.DataSize
				if size < 4 { 
					size = 4 
				}
				px = make([]byte, size-4)
				f.Read(px)
			}
			switch format {
			case 2: 
				target.PxlData = target.Rle8Decode(px)
			case 3: 
				target.PxlData = target.Rle5Decode(px)
			case 4: 
				target.PxlData = target.Lz5Decode(px)
			case 10, 11, 12:
				f.Seek(int64(target.DataOffset)+4, 0)
				img, err := png.Decode(f)
				if err == nil {
					target.IsRaw = true
					buf := new(bytes.Buffer)
					png.Encode(buf, img)
					target.PxlData = buf.Bytes()
				}
			}
		}

		// 读取 v2 调色板
		if !target.IsRaw {
			f.Seek(int64(h.FirstPaletteHeaderOffset)+int64(target.palidx*16), 0)
			var gn_ [3]uint16
			read(&gn_)
			_ = gn_ // 消除未使用警告
			
			var link uint16
			read(&link)
			_ = link // 消除未使用警告
			
			var pofs, plSize uint32
			read(&pofs)
			read(&plSize)
			target.Pal, _ = ReadPalette(f, int64(lofs+pofs), plSize, h.Version[2] != 0)
		}
	}

	// 如果已经是标准 PNG，直接返回
	if target.IsRaw && len(target.PxlData) > 0 && target.PxlData[0] == 0x89 {
		return target.PxlData, nil
	}

	// 将 8 位色像素数据 + 调色板映射，生成最终的 PNG 图像
	if target.Size[0] > 0 && target.Size[1] > 0 && len(target.PxlData) > 0 {
		img := image.NewRGBA(image.Rect(0, 0, int(target.Size[0]), int(target.Size[1])))
		for y := 0; y < int(target.Size[1]); y++ {
			for x := 0; x < int(target.Size[0]); x++ {
				idx := y*int(target.Size[0]) + x
				if idx < len(target.PxlData) {
					colorIdx := target.PxlData[idx]
					if int(colorIdx) < len(target.Pal) {
						c32 := target.Pal[colorIdx]
						r := uint8(c32 & 0xFF)
						g := uint8((c32 >> 8) & 0xFF)
						b := uint8((c32 >> 16) & 0xFF)
						a := uint8((c32 >> 24) & 0xFF)
						img.SetRGBA(x, y, color.RGBA{R: r, G: g, B: b, A: a})
					}
				}
			}
		}
		buf := new(bytes.Buffer)
		png.Encode(buf, img)
		return buf.Bytes(), nil
	}

	return nil, fmt.Errorf("Empty frame data")
}
