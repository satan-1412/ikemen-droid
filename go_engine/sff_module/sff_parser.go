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
	Pal      []uint32
	Group    uint16
	Number   uint16
	Size     [2]uint16
	Offset   [2]int16
	palidx   int
	rle      int
	coldepth byte
	PxlData  []byte // 提纯后的核心：纯净的像素数据
	IsRaw    bool   // 是否已经是真实的 RGBA 数据
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
}

// ==========================================
// 🧩 核心解压算法 (直接从官方提纯，一字未改其逻辑)
// ==========================================

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

// ExtractAllFrames 遍历文件并返回所有帧的基本信息（不提取图像）
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
	read := func(x interface{}) error { return binary.Read(f, binary.LittleEndian, x) }

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
			f.Seek(4, 1) // 跳过 offset
			read(&group)
			read(&number)
			read(&dummy16) // link
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
// 这里的实现包含了原版 SFF v1 和 v2 最核心的读取逻辑，剔除了所有垃圾
func ExtractFrameAsPng(filename string, targetGroup int32, targetItem int32) ([]byte, error) {
	f, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	var h SffHeader
	var lofs, tofs uint32
	h.Read(f, &lofs, &tofs)

	read := func(x interface{}) error { return binary.Read(f, binary.LittleEndian, x) }

	// SFF 解析是一个复杂的遍历过程，这里我们找到目标帧并解压
	shofs := int64(h.FirstSpriteHeaderOffset)
	pl := &PaletteList{}
	pl.init()

	var s *Sprite
	var found bool

	for i := 0; i < int(h.NumberOfSprites); i++ {
		f.Seek(shofs, 0)
		spr := newSprite()
		var xofs, size uint32
		var indexOfPrevious uint16

		if h.Version[0] == 1 {
			read(&xofs)
			read(&size)
			read(&spr.Offset)
			read(&spr.Group)
			read(&spr.Number)
			read(&indexOfPrevious)
		} else {
			read(&spr.Group)
			read(&spr.Number)
			read(&spr.Size)
			read(&spr.Offset)
			read(&indexOfPrevious)
			var format byte
			read(&format)
			spr.rle = -int(format)
			read(&spr.coldepth)
			read(&xofs)
			read(&size)
			var tmp uint16
			read(&tmp)
			spr.palidx = int(tmp)
			read(&tmp)
			if tmp&1 == 0 {
				xofs += lofs
			} else {
				xofs += tofs
			}
		}

		if int32(spr.Group) == targetGroup && int32(spr.Number) == targetItem {
			s = spr
			found = true
			
			if size > 0 {
				if h.Version[0] == 1 {
					var ps byte
					f.Seek(shofs+32, 0)
					read(&ps)
					
					f.Seek(int64(xofs), 0)
					var dummy uint16
					read(&dummy)
					var encoding, bpp byte
					read(&encoding)
					read(&bpp)
					var rect [4]uint16
					read(&rect)
					f.Seek(int64(xofs)+66, 0)
					var bpl uint16
					read(&bpl)
					s.Size[0] = rect[2] - rect[0] + 1
					s.Size[1] = rect[3] - rect[1] + 1
					if encoding == 1 {
						s.rle = int(bpl)
					} else {
						s.rle = 0
					}

					pcxDataStart := int64(xofs) + 128
					paletteOffset := int64(xofs) + int64(size) - 769 // 简化的 PCX 调色板定位
					rleSize := paletteOffset - pcxDataStart
					if rleSize < 0 { rleSize = 0 }

					px := make([]byte, rleSize)
					f.Seek(pcxDataStart, 0)
					read(px)

					var pal []uint32
					s.palidx, pal = pl.NewPal()
					f.Seek(paletteOffset+1, 0)
					var rgb [3]byte
					for c := range pal {
						read(&rgb)
						var alpha byte = 255
						if c == 0 { alpha = 0 }
						pal[c] = uint32(alpha)<<24 | uint32(rgb[2])<<16 | uint32(rgb[1])<<8 | uint32(rgb[0])
					}
					s.PxlData = s.RlePcxDecode(px)
					s.Pal = pal

				} else {
					f.Seek(int64(xofs), 0)
					px := make([]uint8, size)
					read(px)
					if s.rle == 0 {
						if s.coldepth == 8 {
							s.PxlData = px
						} else {
							s.IsRaw = true
							s.PxlData = px
						}
					} else {
						format := -s.rle
						f.Seek(int64(xofs)+4, 0)
						if 2 <= format && format <= 4 {
							if size < 4 { size = 4 }
							px = make([]byte, size-4)
							read(px)
						}
						switch format {
						case 2: s.PxlData = s.Rle8Decode(px)
						case 3: s.PxlData = s.Rle5Decode(px)
						case 4: s.PxlData = s.Lz5Decode(px)
						case 10, 11, 12:
							// 真正的 PNG 解压可以直接通过 Go 原生库
							f.Seek(int64(xofs)+4, 0)
							img, err := png.Decode(f)
							if err == nil {
								s.IsRaw = true
								buf := new(bytes.Buffer)
								png.Encode(buf, img)
								s.PxlData = buf.Bytes()
							}
						}
					}
					// 读取 v2 调色板
					if !s.IsRaw {
						f.Seek(int64(h.FirstPaletteHeaderOffset)+int64(s.palidx*16), 0)
						var gn_ [3]uint16
						read(&gn_)
						var link uint16
						read(&link)
						var pofs, plSize uint32
						read(&pofs)
						read(&plSize)
						s.Pal, _ = ReadPalette(f, int64(lofs+pofs), plSize, h.Version[2] != 0)
					}
				}
			}
			break
		}

		if h.Version[0] == 1 {
			shofs = int64(xofs)
		} else {
			shofs += 28
		}
	}

	if !found || s == nil {
		return nil, fmt.Errorf("Frame not found")
	}

	// 如果已经是标准 PNG，直接返回
	if s.IsRaw && len(s.PxlData) > 0 && s.PxlData[0] == 0x89 {
		return s.PxlData, nil
	}

	// 将 8 位色像素数据 + 调色板映射，生成最终的 PNG 图像
	if s.Size[0] > 0 && s.Size[1] > 0 && len(s.PxlData) > 0 {
		img := image.NewRGBA(image.Rect(0, 0, int(s.Size[0]), int(s.Size[1])))
		for y := 0; y < int(s.Size[1]); y++ {
			for x := 0; x < int(s.Size[0]); x++ {
				idx := y*int(s.Size[0]) + x
				if idx < len(s.PxlData) {
					colorIdx := s.PxlData[idx]
					if int(colorIdx) < len(s.Pal) {
						c32 := s.Pal[colorIdx]
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
