package sff_module

import (
	"bytes"
	"encoding/binary"
	"errors"
	"fmt"
	"image"
	"image/color"
	"image/draw" // 🔥 修复: 补上缺失的 draw 包，用于 SFFv2 的 RGBA 渲染
	_ "image/gif"
	_ "image/jpeg"
	"image/png"
	"io"
	// 🔥 修复: 删除了没有实际使用的 math 包
	"os"
)

func Min(a, b int32) int32 {
	if a < b {
		return a
	}
	return b
}

func Max(a, b int32) int32 {
	if a > b {
		return a
	}
	return b
}

func Btoi(b bool) int {
	if b {
		return 1
	}
	return 0
}

// ==========================================
// 🛠️ 官方 PaletteList 结构 1:1 复刻
// ==========================================

type PaletteList struct {
	palettes   [][]uint32        // 真实的调色板数据
	paletteMap []int             // 逻辑映射
	PalTable   map[[2]uint16]int // Group/Index 到真实索引的映射
	numcols    map[[2]uint16]int
}

func (pl *PaletteList) init() {
	pl.palettes = nil
	pl.paletteMap = nil
	pl.PalTable = make(map[[2]uint16]int)
	pl.numcols = make(map[[2]uint16]int)
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
	if len(pl.paletteMap) == 0 {
		return nil
	}
	if i < 0 || i >= len(pl.paletteMap) {
		i = 0
	}
	return pl.palettes[pl.paletteMap[i]]
}

// ==========================================
// 🛠️ 官方 SFF 核心结构体
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
		return errors.New("unrecognized SFF file, invalid header")
	}
	read := func(x interface{}) error { return binary.Read(r, binary.LittleEndian, x) }
	read(&sh.Version[3])
	read(&sh.Version[2])
	read(&sh.Version[1])
	read(&sh.Version[0])
	var dummy uint32
	read(&dummy)

	switch sh.Version[0] {
	case 1:
		sh.FirstPaletteHeaderOffset, sh.NumberOfPalettes = 0, 0
		read(&sh.NumberOfSprites)
		read(&sh.FirstSpriteHeaderOffset)
		read(&dummy)
	case 2:
		for i := 0; i < 4; i++ {
			read(&dummy)
		}
		read(&sh.FirstSpriteHeaderOffset)
		read(&sh.NumberOfSprites)
		read(&sh.FirstPaletteHeaderOffset)
		read(&sh.NumberOfPalettes)
		read(lofs)
		read(&dummy)
		read(tofs)
	default:
		return errors.New("unrecognized SFF version")
	}
	return nil
}

type Sprite struct {
	Pal        []uint32
	Group      uint16
	Number     uint16
	Size       [2]uint16
	Offset     [2]int16
	palidx     int
	rle        int
	coldepth   byte
	paltemp    []uint32
	// 额外记录原生数据位置，用于无损导出
	PxlData    []byte
	IsRaw      bool
	DataOffset int64
	DataSize   uint32
	RawFormat  byte
}

func newSprite() *Sprite {
	return &Sprite{palidx: -1}
}

func (s *Sprite) shareCopy(src *Sprite) {
	s.Pal = src.Pal
	s.Size = src.Size
	if s.palidx < 0 {
		s.palidx = src.palidx
	}
	s.coldepth = src.coldepth
	s.PxlData = src.PxlData
	s.IsRaw = src.IsRaw
	s.DataOffset = src.DataOffset
	s.DataSize = src.DataSize
	s.RawFormat = src.RawFormat
}

func (s *Sprite) GetPal(pl *PaletteList) []uint32 {
	if len(s.Pal) > 0 || s.coldepth > 8 {
		return s.Pal
	}
	return pl.Get(int(s.palidx))
}

type Sff struct {
	header   SffHeader
	sprites  map[[2]uint16]*Sprite
	palList  PaletteList
	filename string
}

func newSff() *Sff {
	s := &Sff{sprites: make(map[[2]uint16]*Sprite)}
	s.palList.init()
	return s
}

// ==========================================
// 🚀 核心解码引擎 (完全还原版)
// ==========================================

func ReadActPalette(filename string) ([]uint32, error) {
	f, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	data := make([]byte, 768)
	n, _ := io.ReadFull(f, data)
	pal := make([]uint32, 256)
	count := n / 3
	for i := 0; i < count; i++ {
		offset := i * 3
		r := data[offset]
		g := data[offset+1]
		b := data[offset+2]
		destIdx := 255 - i
		if destIdx < 0 {
			break
		}
		var alpha byte = 255
		if destIdx == 0 {
			alpha = 0
		}
		pal[destIdx] = uint32(alpha)<<24 | uint32(b)<<16 | uint32(g)<<8 | uint32(r)
	}
	return pal, nil
}

func (s *Sff) ReadPalette(f io.ReadSeeker, offset int64, size uint32) ([]uint32, error) {
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
			binary.Read(f, binary.LittleEndian, rgba[:])
		}
		// 🔥 修复致命Bug：只有 SFFv1 才需要强制干预 Alpha，SFFv2 必须原样读取！
		if s.header.Version[0] == 1 {
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

func (s *Sprite) readHeader(r io.Reader, ofs, size *uint32, link *uint16) error {
	read := func(x interface{}) error { return binary.Read(r, binary.LittleEndian, x) }
	read(ofs)
	read(size)
	read(s.Offset[:])
	read(&s.Group)
	read(&s.Number)
	read(link)
	return nil
}

func (s *Sprite) readPcxHeader(r io.ReadSeeker, offset int64) error {
	r.Seek(offset, io.SeekStart)
	read := func(rd io.Reader, x interface{}) error { return binary.Read(rd, binary.LittleEndian, x) }
	var dummy uint16
	read(r, &dummy)
	var encoding, bpp byte
	read(r, &encoding)
	read(r, &bpp)
	if bpp != 8 {
		return errors.New("invalid PCX color depth")
	}
	var rect [4]uint16
	read(r, rect[:])
	r.Seek(offset+66, io.SeekStart)
	var bpl uint16
	read(r, &bpl)
	s.Size[0] = rect[2] - rect[0] + 1
	s.Size[1] = rect[3] - rect[1] + 1
	if encoding == 1 {
		s.rle = int(bpl)
	} else {
		s.rle = 0
	}
	return nil
}

func (s *Sprite) RlePcxDecode(rle []byte) (p []byte) {
	if len(rle) == 0 || s.rle <= 0 {
		return rle
	}
	p = make([]byte, int(s.Size[0])*int(s.Size[1]))
	i, j, k, w := 0, 0, 0, int(s.Size[0])
	for j < len(p) {
		n, d := 1, rle[i]
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

func (s *Sprite) read(f io.ReadSeeker, sh *SffHeader, offset int64, datasize uint32, nextSubheader uint32, prev *Sprite, pl *PaletteList) error {
	read := func(x interface{}) error { return binary.Read(f, binary.LittleEndian, x) }
	var ps byte
	read(&ps)
	paletteSame := ps != 0 && prev != nil
	s.readPcxHeader(f, offset)
	pcxHeaderStart := offset
	pcxDataStart := pcxHeaderStart + 128

	var blockEnd int64
	if int64(nextSubheader) > offset {
		blockEnd = int64(nextSubheader)
	} else {
		blockEnd = offset + int64(datasize)
	}

	paletteOffset := int64(-1)
	if !paletteSame {
		var b [1]byte
		scanStart := blockEnd - 769
		scanLimit := pcxDataStart
		for pos := scanStart; pos >= scanLimit; pos-- {
			f.Seek(pos, 0)
			f.Read(b[:])
			if b[0] == 0x0C {
				paletteOffset = pos
				break
			}
		}
		if paletteOffset == -1 {
			paletteOffset = blockEnd - 769
		}
	} else {
		paletteOffset = blockEnd
	}
	rleSize := paletteOffset - pcxDataStart
	if rleSize < 0 {
		rleSize = 0
	}

	px := make([]byte, rleSize)
	f.Seek(pcxDataStart, 0)
	read(px)

	if paletteSame {
		if prev != nil {
			s.palidx = prev.palidx
		}
		if s.palidx < 0 {
			s.palidx, _ = pl.NewPal()
		}
	} else {
		var pal []uint32
		s.palidx, pal = pl.NewPal()
		f.Seek(paletteOffset+1, 0)
		var rgb [3]byte
		for i := range pal {
			read(rgb[:])
			var alpha byte = 255
			if i == 0 {
				alpha = 0
			}
			pal[i] = uint32(alpha)<<24 | uint32(rgb[2])<<16 | uint32(rgb[1])<<8 | uint32(rgb[0])
		}
	}

	s.DataOffset = int64(pcxHeaderStart)
	s.DataSize = datasize
	s.IsRaw = false
	s.PxlData = s.RlePcxDecode(px)
	return nil
}

func (s *Sprite) readHeaderV2(r io.Reader, ofs *uint32, size *uint32, lofs uint32, tofs uint32, link *uint16) error {
	read := func(x interface{}) error { return binary.Read(r, binary.LittleEndian, x) }
	read(&s.Group)
	read(&s.Number)
	read(s.Size[:])
	read(s.Offset[:])
	read(link)
	var format byte
	read(&format)
	s.rle = -int(format)
	s.RawFormat = format
	read(&s.coldepth)
	read(ofs)
	read(size)
	var tmp uint16
	read(&tmp)
	s.palidx = int(tmp)
	read(&tmp)
	if tmp&1 == 0 {
		*ofs += lofs
	} else {
		*ofs += tofs
	}
	return nil
}

func (s *Sprite) Rle8Decode(rle []byte) (p []byte) {
	if len(rle) == 0 {
		return rle
	}
	p = make([]byte, int(s.Size[0])*int(s.Size[1]))
	i, j := 0, 0
	for j < len(p) {
		n, d := 1, rle[i]
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
	i, j := 0, 0
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
	i, j, n := 0, 0, 0
	ct, cts, rb, rbc := rle[i], uint(0), byte(0), uint(0)
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
					rb, rbc = 0, 0
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
			ct, cts = rle[i], 0
			if i < len(rle)-1 {
				i++
			}
		}
	}
	return
}

func (s *Sprite) readV2(f io.ReadSeeker, offset int64, datasize uint32) error {
	var px []byte
	var isRaw bool = false
	s.DataOffset = offset
	s.DataSize = datasize

	if s.rle > 0 {
		return nil
	} else if s.rle == 0 {
		f.Seek(offset, 0)
		px = make([]uint8, datasize)
		binary.Read(f, binary.LittleEndian, px)
		switch s.coldepth {
		case 8:
		case 24, 32:
			isRaw = true
		default:
			return errors.New("unknown color depth")
		}
	} else {
		f.Seek(offset+4, 0)
		format := -s.rle

		if 2 <= format && format <= 4 {
			if datasize < 4 {
				datasize = 4
			}
			px = make([]byte, datasize-4)
			binary.Read(f, binary.LittleEndian, px)
		}

		switch format {
		case 2:
			px = s.Rle8Decode(px)
		case 3:
			px = s.Rle5Decode(px)
		case 4:
			px = s.Lz5Decode(px)
		case 10:
			img, err := png.Decode(f)
			if err != nil {
				return err
			}
			// 🔥 修复Bug：把缺失的 image.Gray 兼容判定完整加回来！
			if pi, ok := img.(*image.Paletted); ok {
				px = pi.Pix
			} else if gray, ok := img.(*image.Gray); ok {
				px = gray.Pix
			} else if rgba, ok := img.(*image.RGBA); ok {
				isRaw = true
				px = rgba.Pix
			}
		case 11, 12:
			isRaw = true
			img, err := png.Decode(f)
			if err != nil {
				return err
			}
			rect := img.Bounds()
			rgba, ok := img.(*image.RGBA)
			if !ok {
				rgba = image.NewRGBA(rect)
				draw.Draw(rgba, rect, img, rect.Min, draw.Src)
			}
			px = rgba.Pix
			s.Size[0] = uint16(rect.Max.X - rect.Min.X)
			s.Size[1] = uint16(rect.Max.Y - rect.Min.Y)
		default:
			return errors.New("unknown format")
		}
	}

	s.IsRaw = isRaw
	s.PxlData = px
	return nil
}

// ==========================================
// 🚀 全局 Context 加载：严格遵守生命周期顺序
// ==========================================

func LoadSffContext(filename string) (*Sff, error) {
	s := newSff()
	s.filename = filename

	f, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	var lofs, tofs uint32
	if err := s.header.Read(f, &lofs, &tofs); err != nil {
		return nil, err
	}

	read := func(x interface{}) error { return binary.Read(f, binary.LittleEndian, x) }

	if s.header.Version[0] != 1 {
		uniquePals := make(map[[2]uint16]int)
		for i := 0; i < int(s.header.NumberOfPalettes); i++ {
			f.Seek(int64(s.header.FirstPaletteHeaderOffset)+int64(i*16), 0)
			var gn_ [3]uint16
			read(gn_[:])
			var link uint16
			read(&link)
			var ofs, plSize uint32
			read(&ofs)
			read(&plSize)
			var pal []uint32
			var idx int
			if old, ok := uniquePals[[2]uint16{gn_[0], gn_[1]}]; ok {
				idx = old
				pal = s.palList.Get(old)
			} else if plSize == 0 {
				idx = int(link)
				pal = s.palList.Get(idx)
			} else {
				pal, _ = s.ReadPalette(f, int64(lofs+ofs), plSize)
				idx = i
			}
			uniquePals[[2]uint16{gn_[0], gn_[1]}] = idx
			s.palList.SetSource(i, pal)
			s.palList.PalTable[[...]uint16{gn_[0], gn_[1]}] = idx
			s.palList.numcols[[...]uint16{gn_[0], gn_[1]}] = int(gn_[2])
		}
	}

	spriteList := make([]*Sprite, int(s.header.NumberOfSprites))
	var prev *Sprite
	shofs := int64(s.header.FirstSpriteHeaderOffset)

	for i := 0; i < len(spriteList); i++ {
		f.Seek(shofs, 0)
		spriteList[i] = newSprite()
		var xofs, size uint32
		var indexOfPrevious uint16
		if s.header.Version[0] == 1 {
			spriteList[i].readHeader(f, &xofs, &size, &indexOfPrevious)
		} else {
			spriteList[i].readHeaderV2(f, &xofs, &size, lofs, tofs, &indexOfPrevious)
		}

		if size == 0 {
			if int(indexOfPrevious) < i {
				spriteList[i].shareCopy(spriteList[int(indexOfPrevious)])
			} else {
				spriteList[i].palidx = 0
			}
		} else {
			if s.header.Version[0] == 1 {
				spriteList[i].read(f, &s.header, shofs+32, size, xofs, prev, &s.palList)
			} else {
				spriteList[i].readV2(f, int64(xofs), size)
			}
			prev = spriteList[i]
		}

		s.sprites[[2]uint16{spriteList[i].Group, spriteList[i].Number}] = spriteList[i]

		if s.header.Version[0] == 1 {
			shofs = int64(xofs)
		} else {
			shofs += 28
		}
	}

	return s, nil
}

// ==========================================
// 🚀 导出与渲染功能
// ==========================================

func RenderSpriteToPng(sff *Sff, group, item uint16, overrideActPath string) ([]byte, error) {
	spr, ok := sff.sprites[[2]uint16{group, item}]
	if !ok || spr == nil {
		return nil, errors.New("frame not found")
	}

	if spr.IsRaw && len(spr.PxlData) > 0 {
		if spr.PxlData[0] == 0x89 {
			return spr.PxlData, nil
		}
		img := image.NewRGBA(image.Rect(0, 0, int(spr.Size[0]), int(spr.Size[1])))
		copy(img.Pix, spr.PxlData)
		buf := new(bytes.Buffer)
		png.Encode(buf, img)
		return buf.Bytes(), nil
	}

	if spr.Size[0] <= 0 || spr.Size[1] <= 0 || len(spr.PxlData) == 0 {
		return nil, errors.New("empty frame data")
	}

	pal := spr.GetPal(&sff.palList)

	// 🔥 支持按键覆写 ACT 调色板 (即使自动，手动依然有效)
	if overrideActPath != "" {
		actPal, err := ReadActPalette(overrideActPath)
		if err == nil && len(actPal) == 256 {
			pal = actPal
		}
	}

	img := image.NewRGBA(image.Rect(0, 0, int(spr.Size[0]), int(spr.Size[1])))
	for y := 0; y < int(spr.Size[1]); y++ {
		for x := 0; x < int(spr.Size[0]); x++ {
			idx := y*int(spr.Size[0]) + x
			if idx < len(spr.PxlData) {
				colorIdx := spr.PxlData[idx]
				if len(pal) > 0 && int(colorIdx) < len(pal) {
					c32 := pal[colorIdx]
					r := uint8(c32 & 0xFF)
					g := uint8((c32 >> 8) & 0xFF)
					b := uint8((c32 >> 16) & 0xFF)
					a := uint8((c32 >> 24) & 0xFF)
					img.SetRGBA(x, y, color.RGBA{R: r, G: g, B: b, A: a})
				} else {
					img.SetRGBA(x, y, color.RGBA{0, 0, 0, 0})
				}
			}
		}
	}
	buf := new(bytes.Buffer)
	png.Encode(buf, img)
	return buf.Bytes(), nil
}

func ExtractRawFrameData(sff *Sff, group, item uint16, overrideActPath string) ([]byte, string, error) {
	spr, ok := sff.sprites[[2]uint16{group, item}]
	if !ok || spr == nil {
		return nil, "png", errors.New("frame not found")
	}

	f, err := os.Open(sff.filename)
	if err != nil {
		return nil, "png", err
	}
	defer f.Close()

	if sff.header.Version[0] == 1 {
		f.Seek(spr.DataOffset, 0)
		pcxData := make([]byte, spr.DataSize)
		f.Read(pcxData)

		pal := spr.GetPal(&sff.palList)
		if overrideActPath != "" {
			actPal, err := ReadActPalette(overrideActPath)
			if err == nil && len(actPal) == 256 {
				pal = actPal
			}
		}

		if len(pcxData) >= 128 {
			hasPalette := false
			if len(pcxData) >= 769 && pcxData[len(pcxData)-769] == 0x0C {
				hasPalette = true
			}
			if !hasPalette {
				pcxData = append(pcxData, 0x0C)
				for i := 0; i < 256; i++ {
					var c uint32 = 0
					if i < len(pal) {
						c = pal[i]
					}
					pcxData = append(pcxData, byte(c&0xFF), byte((c>>8)&0xFF), byte((c>>16)&0xFF))
				}
			} else if overrideActPath != "" && len(pcxData) >= 768 {
				palOffset := len(pcxData) - 768
				for i := 0; i < 256; i++ {
					c := pal[i]
					pcxData[palOffset+i*3] = byte(c & 0xFF)
					pcxData[palOffset+i*3+1] = byte((c >> 8) & 0xFF)
					pcxData[palOffset+i*3+2] = byte((c >> 16) & 0xFF)
				}
			}
		}
		return pcxData, "pcx", nil

	} else {
		format := spr.RawFormat
		if format >= 10 && format <= 12 {
			f.Seek(spr.DataOffset+4, io.SeekStart)
			pngSize := spr.DataSize
			if pngSize > 4 {
				pngSize -= 4
			}
			pngData := make([]byte, pngSize)
			f.Read(pngData)
			return pngData, "png", nil
		}
		pngBytes, err := RenderSpriteToPng(sff, group, item, overrideActPath)
		return pngBytes, "png", err
	}
}

func ParseSffHeaderForApi(filename string) (string, error) {
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
	return fmt.Sprintf("%d.%d%d%d", h.Version[3], h.Version[2], h.Version[1], h.Version[0]), nil
}

type SffFrameInfo struct {
	Group, Item, Width, Height int32
	X, Y                       int16
}

func ExtractAllFramesFromContext(sff *Sff) []SffFrameInfo {
	frames := make([]SffFrameInfo, 0, len(sff.sprites))
	// 简单遍历缓存的 map，因为 SFF 内部已经建立好了关系
	for key, spr := range sff.sprites {
		frames = append(frames, SffFrameInfo{
			Group:  int32(key[0]),
			Item:   int32(key[1]),
			Width:  int32(spr.Size[0]),
			Height: int32(spr.Size[1]),
			X:      spr.Offset[0],
			Y:      spr.Offset[1],
		})
	}
	return frames
}

func ReplaceFrameWithPng(sffPath string, targetGroup int32, targetItem int32, imagePath string) error {
	fileData, err := os.ReadFile(imagePath)
	if err != nil {
		return fmt.Errorf("读取图像失败: %v", err)
	}

	img, format, err := image.Decode(bytes.NewReader(fileData))
	if err != nil {
		return fmt.Errorf("无效的图像格式: %v", err)
	}

	var finalPngData []byte
	if format != "png" {
		buf := new(bytes.Buffer)
		png.Encode(buf, img)
		finalPngData = buf.Bytes()
	} else {
		finalPngData = fileData
	}

	imgConfig := img.Bounds()

	f, err := os.OpenFile(sffPath, os.O_RDWR, 0644)
	if err != nil {
		return fmt.Errorf("无法打开SFF文件: %v", err)
	}
	defer f.Close()

	var h SffHeader
	var lofs, tofs uint32
	if err := h.Read(f, &lofs, &tofs); err != nil {
		return fmt.Errorf("读取SFF头部失败: %v", err)
	}

	if h.Version[0] == 1 {
		return errors.New("SFFv1 仅支持调色板索引模式(PCX-RLE)，无法直接混入现代真彩色PNG！请先将素材转换为 SFFv2 格式")
	}

	shofs := int64(h.FirstSpriteHeaderOffset)
	for i := 0; i < int(h.NumberOfSprites); i++ {
		f.Seek(shofs, io.SeekStart)
		var group, number uint16
		binary.Read(f, binary.LittleEndian, &group)
		binary.Read(f, binary.LittleEndian, &number)

		if int32(group) == targetGroup && int32(number) == targetItem {
			fileInfo, _ := f.Stat()
			appendOffset := fileInfo.Size()

			f.Seek(0, io.SeekEnd)
			dummyHeader := []byte{0, 0, 0, 0}
			f.Write(dummyHeader)
			_, err = f.Write(finalPngData)
			if err != nil {
				return fmt.Errorf("写入PNG数据失败: %v", err)
			}

			f.Seek(shofs+4, io.SeekStart)
			binary.Write(f, binary.LittleEndian, uint16(imgConfig.Dx()))
			binary.Write(f, binary.LittleEndian, uint16(imgConfig.Dy()))

			f.Seek(shofs+14, io.SeekStart)
			binary.Write(f, binary.LittleEndian, byte(11))
			binary.Write(f, binary.LittleEndian, byte(32))

			f.Seek(shofs+26, io.SeekStart)
			binary.Write(f, binary.LittleEndian, uint16(0))

			finalOffset := uint32(appendOffset) - lofs

			f.Seek(shofs+16, io.SeekStart)
			binary.Write(f, binary.LittleEndian, finalOffset)
			binary.Write(f, binary.LittleEndian, uint32(len(finalPngData)+4))

			return nil
		}
		shofs += 28
	}
	return fmt.Errorf("在 SFF 文件中未找到 Group:%d Item:%d", targetGroup, targetItem)
}
