package sff_module

import (
	"bytes"
	"encoding/binary"
	"errors"
	"fmt"
	"image"
	"image/color"
	_ "image/gif"
	_ "image/jpeg"
	"image/png"
	"io"
	"os"
)

// ==========================================
// 🛠️ ACT 解析：支持 WinMugen 倒序灵魂色表挂载
// ==========================================

func ReadActPalette(filename string) ([]uint32, error) {
	data, err := os.ReadFile(filename)
	if err != nil {
		return nil, err
	}
	pal := make([]uint32, 256)
	count := len(data) / 3
	if count > 256 {
		count = 256
	}
	// Mugen 的奇葩机制：正向读取，倒序注入色表 (255 -> 0)
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
		// Index 0 是 Mugen 铁打的透明背景色
		if destIdx == 0 {
			alpha = 0
		}
		pal[destIdx] = uint32(alpha)<<24 | uint32(b)<<16 | uint32(g)<<8 | uint32(r)
	}
	return pal, nil
}

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
		return fmt.Errorf("unrecognized SFF file, invalid header")
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
		return fmt.Errorf("unrecognized SFF version")
	}
	return nil
}

type Sprite struct {
	Pal             []uint32
	Group           uint16
	Number          uint16
	Size            [2]uint16
	Offset          [2]int16
	IndexOfPrevious uint16
	palidx          int
	rle             int
	coldepth        byte
	PxlData         []byte
	IsRaw           bool
	DataOffset      uint32
	DataSize        uint32
	PalOffset       int64
}

func newSprite() *Sprite { return &Sprite{palidx: -1} }

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
	return fmt.Sprintf("%d.%d%d%d", h.Version[3], h.Version[2], h.Version[1], h.Version[0]), nil
}

type SffFrameInfo struct {
	Group, Item, Width, Height int32
	X, Y                       int16
}

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

	frames := make([]SffFrameInfo, 0, h.NumberOfSprites)
	read := func(x interface{}) error { return binary.Read(f, binary.LittleEndian, x) }

	shofs := int64(h.FirstSpriteHeaderOffset)
	for i := 0; i < int(h.NumberOfSprites); i++ {
		f.Seek(shofs, 0)
		var xofs, size uint32
		var group, number, width, height uint16
		var axis [2]int16

		if h.Version[0] == 1 {
			read(&xofs)
			read(&size)
			read(&axis)
			read(&group)
			read(&number)
			if size > 0 {
				currentPos, _ := f.Seek(0, io.SeekCurrent)
				f.Seek(shofs+32+4, io.SeekStart)
				var xmin, ymin, xmax, ymax uint16
				read(&xmin)
				read(&ymin)
				read(&xmax)
				read(&ymax)
				width = xmax - xmin + 1
				height = ymax - ymin + 1
				f.Seek(currentPos, io.SeekStart)
			}
			frames = append(frames, SffFrameInfo{Group: int32(group), Item: int32(number), Width: int32(width), Height: int32(height), X: axis[0], Y: axis[1]})
			shofs = int64(xofs)
		} else {
			read(&group)
			read(&number)
			read(&width)
			read(&height)
			read(&axis)
			frames = append(frames, SffFrameInfo{Group: int32(group), Item: int32(number), Width: int32(width), Height: int32(height), X: axis[0], Y: axis[1]})
			shofs += 28
		}
	}
	return frames, nil
}

// 核心修改：支持将手动寻路的 ACT 文件无缝覆盖给图像进行渲染
func ExtractFrameAsPng(filename string, targetGroup int32, targetItem int32, actPath string) ([]byte, error) {
	f, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	var h SffHeader
	var lofs, tofs uint32
	h.Read(f, &lofs, &tofs)
	read := func(x interface{}) error { return binary.Read(f, binary.LittleEndian, x) }

	sprites := make([]*Sprite, 0, h.NumberOfSprites)
	shofs := int64(h.FirstSpriteHeaderOffset)
	var lastPalOffset int64 = -1

	for i := 0; i < int(h.NumberOfSprites); i++ {
		spr := newSprite()
		f.Seek(shofs, 0)

		if h.Version[0] == 1 {
			var xofs uint32
			var ps byte
			read(&xofs)
			read(&spr.DataSize)
			read(&spr.Offset)
			read(&spr.Group)
			read(&spr.Number)
			read(&spr.IndexOfPrevious)
			read(&ps)
			spr.DataOffset = uint32(shofs + 32)

			if spr.DataSize > 0 {
				if ps == 0 {
					blockEnd := int64(spr.DataOffset + spr.DataSize)
					if int64(xofs) > int64(spr.DataOffset) && int64(xofs) < blockEnd {
						blockEnd = int64(xofs)
					}
					scanStart := blockEnd - 769
					scanLimit := int64(spr.DataOffset + 128)
					palOffset := int64(-1)
					var b [1]byte
					for pos := scanStart; pos >= scanLimit; pos-- {
						f.Seek(pos, 0)
						f.Read(b[:])
						if b[0] == 0x0C {
							palOffset = pos
							break
						}
					}
					if palOffset == -1 {
						palOffset = blockEnd - 769
					}
					spr.PalOffset = palOffset
					lastPalOffset = palOffset
				} else {
					spr.PalOffset = lastPalOffset
				}
			}
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
			var xofs uint32
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
		sprites = append(sprites, spr)
	}

	var target *Sprite
	for _, spr := range sprites {
		if int32(spr.Group) == targetGroup && int32(spr.Number) == targetItem {
			target = spr
			break
		}
	}
	if target == nil {
		return nil, fmt.Errorf("frame not found")
	}

	visited := make(map[uint16]bool)
	currIdx := uint16(0xFFFF)
	for i, s := range sprites {
		if s == target {
			currIdx = uint16(i)
			break
		}
	}

	for target.DataSize == 0 && target.IndexOfPrevious < uint16(len(sprites)) {
		if visited[currIdx] {
			return nil, fmt.Errorf("circular link detected")
		}
		visited[currIdx] = true
		currIdx = target.IndexOfPrevious
		sourceSpr := sprites[currIdx]

		target.DataOffset = sourceSpr.DataOffset
		target.DataSize = sourceSpr.DataSize
		target.rle = sourceSpr.rle
		target.coldepth = sourceSpr.coldepth
		target.palidx = sourceSpr.palidx
		target.PalOffset = sourceSpr.PalOffset
		target.IsRaw = sourceSpr.IsRaw
		if h.Version[0] == 1 {
			target.Size = sourceSpr.Size
		}
	}
	if target.DataSize == 0 {
		return nil, fmt.Errorf("empty linked frame data")
	}

	if h.Version[0] == 1 {
		f.Seek(int64(target.DataOffset), 0)
		var dummy uint16
		read(&dummy)
		var encoding, bpp byte
		read(&encoding)
		read(&bpp)
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
		rleSize := target.PalOffset - pcxDataStart
		if rleSize <= 0 {
			rleSize = int64(target.DataSize) - 128 - 769
		}
		if rleSize < 0 {
			rleSize = 0
		}

		px := make([]byte, rleSize)
		f.Seek(pcxDataStart, 0)
		f.Read(px)
		target.PxlData = target.RlePcxDecode(px)

		if target.PalOffset > 0 {
			pal := make([]uint32, 256)
			f.Seek(target.PalOffset+1, 0)
			var rgb [3]byte
			for c := 0; c < 256; c++ {
				f.Read(rgb[:])
				var alpha byte = 255
				if c == 0 {
					alpha = 0
				} // 强制透明度修正
				pal[c] = uint32(alpha)<<24 | uint32(rgb[2])<<16 | uint32(rgb[1])<<8 | uint32(rgb[0])
			}
			target.Pal = pal
		}
	} else {
		f.Seek(int64(target.DataOffset), 0)
		format := -target.rle

		if format == 0 {
			px := make([]uint8, target.DataSize)
			f.Read(px)
			if target.coldepth == 8 {
				target.PxlData = px
			} else {
				target.IsRaw = true
				target.PxlData = px
			}
		} else {
			if 2 <= format && format <= 4 {
				f.Seek(int64(target.DataOffset)+4, 0)
				size := target.DataSize
				if size < 4 {
					size = 4
				}
				px := make([]byte, size-4)
				f.Read(px)
				switch format {
				case 2:
					target.PxlData = target.Rle8Decode(px)
				case 3:
					target.PxlData = target.Rle5Decode(px)
				case 4:
					target.PxlData = target.Lz5Decode(px)
				}
			} else if format >= 10 && format <= 12 {
				f.Seek(int64(target.DataOffset)+4, 0)
				img, err := png.Decode(f)
				if err == nil {
					if format == 10 {
						if pi, ok := img.(*image.Paletted); ok {
							target.PxlData = pi.Pix
							target.Size[0] = uint16(pi.Rect.Dx())
							target.Size[1] = uint16(pi.Rect.Dy())
							target.IsRaw = false
						} else if gray, ok := img.(*image.Gray); ok {
							target.PxlData = gray.Pix
							target.Size[0] = uint16(gray.Rect.Dx())
							target.Size[1] = uint16(gray.Rect.Dy())
							target.IsRaw = false
						}
					} else {
						target.IsRaw = true
						buf := new(bytes.Buffer)
						png.Encode(buf, img)
						target.PxlData = buf.Bytes()
					}
				}
			}
		}

		if !target.IsRaw && len(target.Pal) == 0 {
			palidx := target.palidx
			found := false
			for loopLimit := 0; loopLimit < 10; loopLimit++ {
				f.Seek(int64(h.FirstPaletteHeaderOffset)+int64(palidx*16), 0)
				var gn [3]uint16
				read(&gn)
				var link uint16
				read(&link)
				var pofs, plSize uint32
				read(&pofs)
				read(&plSize)

				if plSize == 0 {
					palidx = int(link)
				} else {
					target.Pal, _ = ReadPalette(f, int64(lofs+pofs), plSize, h.Version[2] != 0)
					found = true
					break
				}
			}
			if !found && h.NumberOfPalettes > 0 {
				f.Seek(int64(h.FirstPaletteHeaderOffset), 0)
				f.Seek(6, io.SeekCurrent)
				var pofs, plSize uint32
				read(&pofs)
				read(&plSize)
				if plSize > 0 {
					target.Pal, _ = ReadPalette(f, int64(lofs+pofs), plSize, h.Version[2] != 0)
				}
			}
		}
	}

	// ======================================
	// 🚀 方案 A / B 核心生效区：挂载外部 ACT 灵魂
	// ======================================
	if actPath != "" && !target.IsRaw {
		actPal, err := ReadActPalette(actPath)
		if err == nil && len(actPal) == 256 {
			target.Pal = actPal
		}
	}

	if target.IsRaw && len(target.PxlData) > 0 {
		if target.PxlData[0] == 0x89 {
			return target.PxlData, nil
		}
		img := image.NewRGBA(image.Rect(0, 0, int(target.Size[0]), int(target.Size[1])))
		copy(img.Pix, target.PxlData)
		buf := new(bytes.Buffer)
		png.Encode(buf, img)
		return buf.Bytes(), nil
	}

	if target.Size[0] > 0 && target.Size[1] > 0 && len(target.PxlData) > 0 {
		img := image.NewRGBA(image.Rect(0, 0, int(target.Size[0]), int(target.Size[1])))
		for y := 0; y < int(target.Size[1]); y++ {
			for x := 0; x < int(target.Size[0]); x++ {
				idx := y*int(target.Size[0]) + x
				if idx < len(target.PxlData) {
					colorIdx := target.PxlData[idx]
					// 🔥 核心病理修复：严格执行 Mugen 色表规范，0 号索引强制为纯粹的抠像透明！
					if colorIdx == 0 {
						img.SetRGBA(x, y, color.RGBA{0, 0, 0, 0})
					} else if len(target.Pal) > 0 && int(colorIdx) < len(target.Pal) {
						c32 := target.Pal[colorIdx]
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
	return nil, fmt.Errorf("empty frame data")
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

// ==========================================
// 🚀 全新底层：提取原汁原味的二进制数据用于无损导出
// ==========================================

func ExtractRawFrameData(filename string, targetGroup int32, targetItem int32, actPath string) ([]byte, string, error) {
	f, err := os.Open(filename)
	if err != nil {
		return nil, "png", err
	}
	defer f.Close()

	var h SffHeader
	var lofs, tofs uint32
	h.Read(f, &lofs, &tofs)
	read := func(x interface{}) error { return binary.Read(f, binary.LittleEndian, x) }

	sprites := make([]*Sprite, 0, h.NumberOfSprites)
	shofs := int64(h.FirstSpriteHeaderOffset)
	var lastPalOffset int64 = -1

	// 精确锁定并解析 Sprites（逻辑与渲染端严格一致）
	for i := 0; i < int(h.NumberOfSprites); i++ {
		spr := newSprite()
		f.Seek(shofs, 0)
		if h.Version[0] == 1 {
			var xofs uint32
			var ps byte
			read(&xofs)
			read(&spr.DataSize)
			read(&spr.Offset)
			read(&spr.Group)
			read(&spr.Number)
			read(&spr.IndexOfPrevious)
			read(&ps)
			spr.DataOffset = uint32(shofs + 32)
			if spr.DataSize > 0 {
				if ps == 0 {
					blockEnd := int64(spr.DataOffset + spr.DataSize)
					if int64(xofs) > int64(spr.DataOffset) && int64(xofs) < blockEnd {
						blockEnd = int64(xofs)
					}
					scanStart := blockEnd - 769
					scanLimit := int64(spr.DataOffset + 128)
					palOffset := int64(-1)
					var b [1]byte
					for pos := scanStart; pos >= scanLimit; pos-- {
						f.Seek(pos, 0)
						f.Read(b[:])
						if b[0] == 0x0C {
							palOffset = pos
							break
						}
					}
					if palOffset == -1 {
						palOffset = blockEnd - 769
					}
					spr.PalOffset = palOffset
					lastPalOffset = palOffset
				} else {
					spr.PalOffset = lastPalOffset
				}
			}
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
			var xofs uint32
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
		sprites = append(sprites, spr)
	}

	var target *Sprite
	for _, spr := range sprites {
		if int32(spr.Group) == targetGroup && int32(spr.Number) == targetItem {
			target = spr
			break
		}
	}
	if target == nil {
		return nil, "png", fmt.Errorf("frame not found")
	}

	// 追踪关联帧
	visited := make(map[uint16]bool)
	currIdx := uint16(0xFFFF)
	for i, s := range sprites {
		if s == target {
			currIdx = uint16(i)
			break
		}
	}
	for target.DataSize == 0 && target.IndexOfPrevious < uint16(len(sprites)) {
		if visited[currIdx] {
			return nil, "png", fmt.Errorf("circular link detected")
		}
		visited[currIdx] = true
		currIdx = target.IndexOfPrevious
		sourceSpr := sprites[currIdx]
		target.DataOffset = sourceSpr.DataOffset
		target.DataSize = sourceSpr.DataSize
		target.rle = sourceSpr.rle
		target.coldepth = sourceSpr.coldepth
		target.palidx = sourceSpr.palidx
		target.PalOffset = sourceSpr.PalOffset
		target.IsRaw = sourceSpr.IsRaw
		if h.Version[0] == 1 {
			target.Size = sourceSpr.Size
		}
	}

	// 解析调色板以便注入 PCX
	if h.Version[0] == 1 && target.PalOffset > 0 {
		pal := make([]uint32, 256)
		f.Seek(target.PalOffset+1, 0)
		var rgb [3]byte
		for c := 0; c < 256; c++ {
			f.Read(rgb[:])
			var alpha byte = 255
			if c == 0 {
				alpha = 0
			}
			pal[c] = uint32(alpha)<<24 | uint32(rgb[2])<<16 | uint32(rgb[1])<<8 | uint32(rgb[0])
		}
		target.Pal = pal
	}

	// 注入外部 ACT (最高优先级)
	if actPath != "" && !target.IsRaw {
		actPal, err := ReadActPalette(actPath)
		if err == nil && len(actPal) == 256 {
			target.Pal = actPal
		}
	}

	if h.Version[0] == 1 {
		// 🛠️ SFFv1: 完美重建 PCX 结构
		f.Seek(int64(target.DataOffset), 0)
		pcxData := make([]byte, target.DataSize)
		f.Read(pcxData)

		if len(pcxData) >= 128 {
			hasPalette := false
			if len(pcxData) >= 769 && pcxData[len(pcxData)-769] == 0x0C {
				hasPalette = true
			}

			if !hasPalette {
				// 自动补齐 0x0C 标志位和 768 字节调色板尾巴
				pcxData = append(pcxData, 0x0C)
				for i := 0; i < 256; i++ {
					var c uint32 = 0
					if i < len(target.Pal) {
						c = target.Pal[i]
					}
					pcxData = append(pcxData, byte(c&0xFF), byte((c>>8)&0xFF), byte((c>>16)&0xFF))
				}
			} else if actPath != "" && len(pcxData) >= 768 {
				// 如果有自带调色板且用户挂载了 ACT，我们在二进制层面强行把新色表覆写进文件尾部！
				palOffset := len(pcxData) - 768
				for i := 0; i < 256; i++ {
					c := target.Pal[i]
					pcxData[palOffset+i*3] = byte(c & 0xFF)
					pcxData[palOffset+i*3+1] = byte((c >> 8) & 0xFF)
					pcxData[palOffset+i*3+2] = byte((c >> 16) & 0xFF)
				}
			}
		}
		return pcxData, "pcx", nil

	} else {
		// 🛠️ SFFv2: PNG 剥离机制
		format := -target.rle
		if format >= 10 && format <= 12 {
			f.Seek(int64(target.DataOffset)+4, io.SeekStart)
			pngSize := target.DataSize
			if pngSize > 4 {
				pngSize -= 4
			}
			pngData := make([]byte, pngSize)
			f.Read(pngData)
			return pngData, "png", nil
		}

		// SFFv2 的 RLE/LZ5 压缩，直接调用图像解码后封装为标准 PNG 导出
		f.Close() // 必须先关闭句柄，防止双开冲突
		pngBytes, err := ExtractFrameAsPng(filename, targetGroup, targetItem, actPath)
		return pngBytes, "png", err
	}
}

