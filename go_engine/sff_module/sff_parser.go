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
	"path/filepath"
	"strings"
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

func findSpriteTarget(f *os.File, h *SffHeader, lofs uint32, tofs uint32, targetGroup int32, targetItem int32) (*Sprite, []*Sprite, error) {
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
		return nil, nil, fmt.Errorf("frame not found")
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
			return nil, nil, fmt.Errorf("circular link detected")
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
		return nil, nil, fmt.Errorf("empty linked frame data")
	}
	return target, sprites, nil
}

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

	target, _, err := findSpriteTarget(f, &h, lofs, tofs, targetGroup, targetItem)
	if err != nil {
		return nil, err
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
		var rleSize int64
		if target.PalOffset > pcxDataStart && target.PalOffset <= int64(target.DataOffset+target.DataSize) {
			rleSize = target.PalOffset - pcxDataStart
		} else {
			rleSize = int64(target.DataSize) - 128
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
				}
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
				pngData := make([]byte, target.DataSize-4)
				f.Read(pngData)
				img, err := png.Decode(bytes.NewReader(pngData))
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
					if len(target.Pal) > 0 && int(colorIdx) < len(target.Pal) {
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

func ExportFrameNative(filename string, targetGroup int32, targetItem int32, actPath string, outDir string) (string, error) {
	f, err := os.Open(filename)
	if err != nil {
		return "", err
	}
	defer f.Close()

	var h SffHeader
	var lofs, tofs uint32
	h.Read(f, &lofs, &tofs)

	target, _, err := findSpriteTarget(f, &h, lofs, tofs, targetGroup, targetItem)
	if err != nil {
		return "", err
	}

	charName := strings.TrimSuffix(filepath.Base(filename), filepath.Ext(filename))
	baseFileName := fmt.Sprintf("%s_G%d_I%d", charName, targetGroup, targetItem)

	if h.Version[0] == 1 {
		pcxHeader := make([]byte, 128)
		f.Seek(int64(target.DataOffset), 0)
		f.Read(pcxHeader)

		pcxDataStart := int64(target.DataOffset) + 128
		var rleSize int64
		if target.PalOffset > pcxDataStart && target.PalOffset <= int64(target.DataOffset+target.DataSize) {
			rleSize = target.PalOffset - pcxDataStart
		} else {
			rleSize = int64(target.DataSize) - 128
		}
		if rleSize < 0 {
			rleSize = 0
		}

		rleData := make([]byte, rleSize)
		f.Seek(pcxDataStart, 0)
		f.Read(rleData)

		var pcxPalette []byte
		if actPath != "" {
			actBytes, err := os.ReadFile(actPath)
			if err == nil {
				pcxPalette = make([]byte, 768)
				count := len(actBytes) / 3
				if count > 256 {
					count = 256
				}
				for i := 0; i < count; i++ {
					destIdx := 255 - i
					pcxPalette[destIdx*3] = actBytes[i*3]
					pcxPalette[destIdx*3+1] = actBytes[i*3+1]
					pcxPalette[destIdx*3+2] = actBytes[i*3+2]
				}
			}
		}

		if pcxPalette == nil && target.PalOffset > 0 {
			pcxPalette = make([]byte, 768)
			f.Seek(target.PalOffset+1, 0)
			f.Read(pcxPalette)
		}

		outPath := filepath.Join(outDir, baseFileName+".pcx")
		outFile, err := os.Create(outPath)
		if err != nil {
			return "", err
		}
		defer outFile.Close()

		outFile.Write(pcxHeader)
		outFile.Write(rleData)
		if pcxPalette != nil {
			outFile.Write([]byte{0x0C})
			outFile.Write(pcxPalette)
		}
		return outPath, nil

	} else {
		format := -target.rle
		if format >= 10 && format <= 12 {
			f.Seek(int64(target.DataOffset)+4, 0)
			pngData := make([]byte, target.DataSize-4)
			f.Read(pngData)
			outPath := filepath.Join(outDir, baseFileName+".png")
			os.WriteFile(outPath, pngData, 0644)
			return outPath, nil
		} else {
			pngBytes, err := ExtractFrameAsPng(filename, targetGroup, targetItem, actPath)
			if err != nil {
				return "", err
			}
			outPath := filepath.Join(outDir, baseFileName+".png")
			os.WriteFile(outPath, pngBytes, 0644)
			return outPath, nil
		}
	}
}

// ==========================================
// 🚀 新增编辑功能 (独立附加于原文件末尾，绝不干扰读取功能)
// ==========================================

func ReplaceFrameWithPng(sffPath string, targetGroup int32, targetItem int32, axisX int16, axisY int16, imagePath string) error {
	fileData, err := os.ReadFile(imagePath)
	if err != nil { return fmt.Errorf("读取图像失败: %v", err) }

	isPcx := (len(fileData) > 0 && fileData[0] == 0x0A) || strings.HasSuffix(strings.ToLower(imagePath), ".pcx")
	var finalData []byte
	var width, height uint16

	if isPcx {
		finalData = fileData
		if len(fileData) >= 12 {
			width = binary.LittleEndian.Uint16(fileData[8:10]) - binary.LittleEndian.Uint16(fileData[4:6]) + 1
			height = binary.LittleEndian.Uint16(fileData[10:12]) - binary.LittleEndian.Uint16(fileData[6:8]) + 1
		}
	} else {
		img, format, err := image.Decode(bytes.NewReader(fileData))
		if err != nil { return fmt.Errorf("无效的图像格式: %v", err) }
		if format != "png" {
			buf := new(bytes.Buffer)
			png.Encode(buf, img)
			finalData = buf.Bytes()
		} else { finalData = fileData }
		width = uint16(img.Bounds().Dx()); height = uint16(img.Bounds().Dy())
	}

	f, err := os.OpenFile(sffPath, os.O_RDWR, 0644)
	if err != nil { return fmt.Errorf("无法打开SFF文件: %v", err) }
	defer f.Close()

	var h SffHeader
	var lofs, tofs uint32
	if err := h.Read(f, &lofs, &tofs); err != nil { return fmt.Errorf("读取SFF头部失败: %v", err) }

	if h.Version[0] == 1 {
		if !isPcx { return errors.New("SFFv1 底层仅支持 PCX 格式替换") }
		shofs := int64(h.FirstSpriteHeaderOffset)
		var prevShofs int64 = 0
		for i := 0; i < int(h.NumberOfSprites); i++ {
			f.Seek(shofs, io.SeekStart)
			var nextOffset uint32
			binary.Read(f, binary.LittleEndian, &nextOffset)
			f.Seek(shofs+12, io.SeekStart)
			var group, number uint16
			binary.Read(f, binary.LittleEndian, &group)
			binary.Read(f, binary.LittleEndian, &number)

			if int32(group) == targetGroup && int32(number) == targetItem {
				origHeader := make([]byte, 32)
				f.Seek(shofs, io.SeekStart)
				f.Read(origHeader)
				binary.LittleEndian.PutUint32(origHeader[4:8], uint32(len(finalData)))
				binary.LittleEndian.PutUint16(origHeader[8:10], uint16(axisX))
				binary.LittleEndian.PutUint16(origHeader[10:12], uint16(axisY))
				f.Seek(0, io.SeekEnd)
				eofOffset, _ := f.Seek(0, io.SeekCurrent)
				f.Write(origHeader)
				f.Write(finalData)
				if prevShofs == 0 {
					f.Seek(24, io.SeekStart)
					binary.Write(f, binary.LittleEndian, uint32(eofOffset))
				} else {
					f.Seek(prevShofs, io.SeekStart)
					binary.Write(f, binary.LittleEndian, uint32(eofOffset))
				}
				return nil
			}
			if nextOffset == 0 { break }
			prevShofs = shofs
			shofs = int64(nextOffset)
		}
		return fmt.Errorf("在 SFF 文件中未找到 Group:%d Item:%d", targetGroup, targetItem)
	} else {
		if isPcx { return errors.New("SFFv2 请使用 PNG 进行替换") }
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
				_, err = f.Write(finalData)
				if err != nil { return fmt.Errorf("写入数据失败: %v", err) }
				
				f.Seek(shofs+4, io.SeekStart)
				binary.Write(f, binary.LittleEndian, width)
				binary.Write(f, binary.LittleEndian, height)
				binary.Write(f, binary.LittleEndian, uint16(axisX))
				binary.Write(f, binary.LittleEndian, uint16(axisY))
				
				f.Seek(shofs+14, io.SeekStart)
				binary.Write(f, binary.LittleEndian, byte(11))
				binary.Write(f, binary.LittleEndian, byte(32))
				f.Seek(shofs+26, io.SeekStart)
				binary.Write(f, binary.LittleEndian, uint16(0))
				
				// 🟢 极其关键：读取原帧的 Flags 决定我们该使用哪个块的偏移
				var origFlags uint16
				f.Seek(shofs+26, io.SeekStart)
				binary.Read(f, binary.LittleEndian, &origFlags)

				var finalOffset uint32
				if origFlags&1 == 0 {
					finalOffset = uint32(appendOffset) - lofs
				} else {
					finalOffset = uint32(appendOffset) - tofs
				}

				f.Seek(shofs+16, io.SeekStart)
				binary.Write(f, binary.LittleEndian, finalOffset)
				binary.Write(f, binary.LittleEndian, uint32(len(finalData)+4))

				// 🟢 极其关键：必须向后移 4 个字节，写到表示 Length 的专属位置 (56 和 64)，绝对不能覆写起始坐标
				f.Seek(0, io.SeekEnd)
				newEof, _ := f.Seek(0, io.SeekCurrent)
				if origFlags&1 == 0 {
					f.Seek(56, io.SeekStart) // 仅覆盖 ldata length
					binary.Write(f, binary.LittleEndian, uint32(newEof)-lofs)
				} else {
					f.Seek(64, io.SeekStart) // 仅覆盖 tdata length
					binary.Write(f, binary.LittleEndian, uint32(newEof)-tofs)
				}
				return nil
			}
			shofs += 28
		}
		return fmt.Errorf("在 SFF 文件中未找到 Group:%d Item:%d", targetGroup, targetItem)
	}
}

func AddFrameWithPng(sffPath string, targetGroup int32, targetItem int32, axisX int16, axisY int16, imagePath string) error {
	fileData, err := os.ReadFile(imagePath)
	if err != nil { return err }

	isPcx := (len(fileData) > 0 && fileData[0] == 0x0A) || strings.HasSuffix(strings.ToLower(imagePath), ".pcx")
	var finalData []byte
	var width, height uint16

	if isPcx {
		finalData = fileData
		if len(fileData) >= 12 {
			width = binary.LittleEndian.Uint16(fileData[8:10]) - binary.LittleEndian.Uint16(fileData[4:6]) + 1
			height = binary.LittleEndian.Uint16(fileData[10:12]) - binary.LittleEndian.Uint16(fileData[6:8]) + 1
		}
	} else {
		img, format, err := image.Decode(bytes.NewReader(fileData))
		if err != nil { return err }
		if format != "png" {
			buf := new(bytes.Buffer)
			png.Encode(buf, img)
			finalData = buf.Bytes()
		} else { finalData = fileData }
		width = uint16(img.Bounds().Dx()); height = uint16(img.Bounds().Dy())
	}

	f, err := os.OpenFile(sffPath, os.O_RDWR, 0644)
	if err != nil { return err }
	defer f.Close()

	fileInfo, _ := f.Stat()
	if fileInfo.Size() == 0 {
		blankHeader := make([]byte, 512)
		copy(blankHeader[0:12], "ElecbyteSpr\x00")
		blankHeader[12] = 0; blankHeader[13] = 0; blankHeader[14] = 0; blankHeader[15] = 2 
		// 🟢 极其关键：校准坐标！之前把512写到了[40:44]里，导致被引擎当做有512张图！
		binary.LittleEndian.PutUint32(blankHeader[36:40], 512) // 首帧头部偏移量 (36)
		binary.LittleEndian.PutUint32(blankHeader[40:44], 0)   // 真正的图片总数，设为0 (40)
		binary.LittleEndian.PutUint32(blankHeader[44:48], 0)   // 色表偏移量 (44)
		binary.LittleEndian.PutUint32(blankHeader[48:52], 0)   // 色表总数 (48)
		binary.LittleEndian.PutUint32(blankHeader[52:56], 512) // ldata起始位置 (52)
		binary.LittleEndian.PutUint32(blankHeader[56:60], 0)   // ldata长度 (56)
		binary.LittleEndian.PutUint32(blankHeader[60:64], 512) // tdata起始位置 (60)
		binary.LittleEndian.PutUint32(blankHeader[64:68], 0)   // tdata长度 (64)
		f.Write(blankHeader)
		f.Seek(0, io.SeekStart)
	}

	var h SffHeader
	var lofs, tofs uint32
	if err := h.Read(f, &lofs, &tofs); err != nil { return err }

	f.Seek(0, io.SeekEnd)
	eofOffset, _ := f.Seek(0, io.SeekCurrent)

	if h.Version[0] == 1 {
		if !isPcx { return errors.New("SFFv1 仅支持 PCX 追加") }
		newHeader := make([]byte, 32)
		binary.LittleEndian.PutUint32(newHeader[0:4], 0) 
		binary.LittleEndian.PutUint32(newHeader[4:8], uint32(len(finalData)))
		binary.LittleEndian.PutUint16(newHeader[8:10], uint16(axisX))
		binary.LittleEndian.PutUint16(newHeader[10:12], uint16(axisY))
		binary.LittleEndian.PutUint16(newHeader[12:14], uint16(targetGroup))
		binary.LittleEndian.PutUint16(newHeader[14:16], uint16(targetItem))
		binary.LittleEndian.PutUint16(newHeader[16:18], uint16(0))
		newHeader[18] = 0

		f.Write(newHeader)
		f.Write(finalData)

		shofs := int64(h.FirstSpriteHeaderOffset)
		var prevShofs int64 = 0
		for i := 0; i < int(h.NumberOfSprites); i++ {
			f.Seek(shofs, io.SeekStart)
			var nextOffset uint32
			binary.Read(f, binary.LittleEndian, &nextOffset)
			if nextOffset == 0 { prevShofs = shofs; break }
			shofs = int64(nextOffset)
		}

		if prevShofs == 0 && h.NumberOfSprites == 0 {
			f.Seek(24, io.SeekStart)
			binary.Write(f, binary.LittleEndian, uint32(eofOffset))
		} else if prevShofs > 0 {
			f.Seek(prevShofs, io.SeekStart)
			binary.Write(f, binary.LittleEndian, uint32(eofOffset))
		}
		h.NumberOfSprites++
		f.Seek(20, io.SeekStart) 
		binary.Write(f, binary.LittleEndian, h.NumberOfSprites)

	} else {
		if isPcx { return errors.New("SFFv2 请使用 PNG 追加") }

		dummyHeader := []byte{0, 0, 0, 0}
		f.Write(dummyHeader)
		f.Write(finalData)
        
		var oldHeadersData []byte
		if h.NumberOfSprites > 0 {
			oldHeadersData = make([]byte, int(h.NumberOfSprites)*28)
			f.Seek(int64(h.FirstSpriteHeaderOffset), io.SeekStart)
			f.Read(oldHeadersData)
		}

		newSpriteHeader := make([]byte, 28)
		binary.LittleEndian.PutUint16(newSpriteHeader[0:2], uint16(targetGroup))
		binary.LittleEndian.PutUint16(newSpriteHeader[2:4], uint16(targetItem))
		binary.LittleEndian.PutUint16(newSpriteHeader[4:6], width)
		binary.LittleEndian.PutUint16(newSpriteHeader[6:8], height)
		binary.LittleEndian.PutUint16(newSpriteHeader[8:10], uint16(axisX))
		binary.LittleEndian.PutUint16(newSpriteHeader[10:12], uint16(axisY))
		binary.LittleEndian.PutUint16(newSpriteHeader[12:14], uint16(h.NumberOfSprites))
		newSpriteHeader[14] = 11 
		newSpriteHeader[15] = 32
		binary.LittleEndian.PutUint32(newSpriteHeader[16:20], uint32(eofOffset)-tofs)
		binary.LittleEndian.PutUint32(newSpriteHeader[20:24], uint32(len(finalData)+4))
		binary.LittleEndian.PutUint16(newSpriteHeader[24:26], 0)
		binary.LittleEndian.PutUint16(newSpriteHeader[26:28], 1) // 🟢 极其关键：写入 Flags = 1，明确告诉引擎这是 tdata 数据

		f.Seek(0, io.SeekEnd)
		// 记录图片写完后的真实数据末尾，作为 tdata 的长度标尺
		tdataEndOffset, _ := f.Seek(0, io.SeekCurrent)
		newHeaderListOffset := tdataEndOffset

		if len(oldHeadersData) > 0 {
			f.Write(oldHeadersData)
		}
		f.Write(newSpriteHeader)

		h.NumberOfSprites++
		h.FirstSpriteHeaderOffset = uint32(newHeaderListOffset)

		// 🟢 极其关键：更新 tdata 长度！(真正的长度字节在 64！之前的 60 把起始坐标给覆盖了，导致花屏)
		f.Seek(64, io.SeekStart)
		binary.Write(f, binary.LittleEndian, uint32(tdataEndOffset)-tofs)

		// 🟢 极其关键：Sprite 图片总数的更新必须从 36 开始写，而不是 32！
		f.Seek(36, io.SeekStart)
		binary.Write(f, binary.LittleEndian, h.FirstSpriteHeaderOffset)
		binary.Write(f, binary.LittleEndian, h.NumberOfSprites)
	}
	return nil
}

func DeleteFrame(sffPath string, targetGroup int32, targetItem int32) error {
	f, err := os.OpenFile(sffPath, os.O_RDWR, 0644)
	if err != nil { return err }
	defer f.Close()

	var h SffHeader
	var lofs, tofs uint32
	if err := h.Read(f, &lofs, &tofs); err != nil { return err }

	if h.Version[0] == 1 {
		shofs := int64(h.FirstSpriteHeaderOffset)
		var prevShofs int64 = 0

		for i := 0; i < int(h.NumberOfSprites); i++ {
			f.Seek(shofs, io.SeekStart)
			var nextOffset uint32
			binary.Read(f, binary.LittleEndian, &nextOffset)

			f.Seek(shofs+12, io.SeekStart)
			var group, number uint16
			binary.Read(f, binary.LittleEndian, &group)
			binary.Read(f, binary.LittleEndian, &number)

			if int32(group) == targetGroup && int32(number) == targetItem {
				if prevShofs == 0 {
					f.Seek(24, io.SeekStart)
					binary.Write(f, binary.LittleEndian, nextOffset)
				} else {
					f.Seek(prevShofs, io.SeekStart)
					binary.Write(f, binary.LittleEndian, nextOffset)
				}
				h.NumberOfSprites--
				f.Seek(20, io.SeekStart)
				binary.Write(f, binary.LittleEndian, h.NumberOfSprites)
				return nil
			}
			prevShofs = shofs
			shofs = int64(nextOffset)
		}
	} else {
		shofs := int64(h.FirstSpriteHeaderOffset)
		for i := 0; i < int(h.NumberOfSprites); i++ {
			f.Seek(shofs, io.SeekStart)
			var group, number uint16
			binary.Read(f, binary.LittleEndian, &group)
			binary.Read(f, binary.LittleEndian, &number)

			if int32(group) == targetGroup && int32(number) == targetItem {
				f.Seek(shofs, io.SeekStart)
				binary.Write(f, binary.LittleEndian, uint16(0xFFFF))
				binary.Write(f, binary.LittleEndian, uint16(0xFFFF))
				binary.Write(f, binary.LittleEndian, uint16(0))
				binary.Write(f, binary.LittleEndian, uint16(0))
				return nil
			}
			shofs += 28
		}
	}
	return fmt.Errorf("未找到需要删除的帧")
}

func ExtractInternalPalette(sffPath string, outActPath string) error {
	f, err := os.Open(sffPath)
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
		shofs := int64(h.FirstSpriteHeaderOffset)
		f.Seek(shofs+4, io.SeekStart)
		var dataLen uint32
		binary.Read(f, binary.LittleEndian, &dataLen)

		if dataLen >= 768 {
			actData := make([]byte, 768)
			f.Seek(shofs+32+int64(dataLen)-768, io.SeekStart)
			f.Read(actData)
			return os.WriteFile(outActPath, actData, 0644)
		}
		return errors.New("SFFv1 第一帧数据异常，无法提取内置色表")
	} else {
		f.Seek(36, io.SeekStart)
		var palOffset uint32
		binary.Read(f, binary.LittleEndian, &palOffset)

		if palOffset == 0 {
			return errors.New("该 SFFv2 不存在内置色表节点")
		}

		f.Seek(int64(palOffset)+8, io.SeekStart)
		var dataOffset, dataLength uint32
		binary.Read(f, binary.LittleEndian, &dataOffset)
		binary.Read(f, binary.LittleEndian, &dataLength)

		if dataLength > 0 {
			palData := make([]byte, dataLength)
			f.Seek(int64(lofs+dataOffset), io.SeekStart)
			f.Read(palData)
			return os.WriteFile(outActPath, palData, 0644)
		}
		return errors.New("SFFv2 色表节点数据为空")
	}
}

func InjectInternalPalette(sffPath string, actPath string) error {
	actData, err := os.ReadFile(actPath)
	if err != nil {
		return err
	}

	f, err := os.OpenFile(sffPath, os.O_RDWR, 0644)
	if err != nil {
		return err
	}
	defer f.Close()

	var h SffHeader
	var lofs, tofs uint32
	if err := h.Read(f, &lofs, &tofs); err != nil {
		return err
	}

	if h.Version[0] == 1 {
		if len(actData) != 768 {
			return errors.New("SFFv1 仅接受 768 字节的色表")
		}
		shofs := int64(h.FirstSpriteHeaderOffset)
		f.Seek(shofs+4, io.SeekStart)
		var dataLen uint32
		binary.Read(f, binary.LittleEndian, &dataLen)

		if dataLen >= 768 {
			f.Seek(shofs+32+int64(dataLen)-768, io.SeekStart)
			f.Write(actData)
			return nil
		}
		return errors.New("SFFv1 写入内置色表失败")
	} else {
		if len(actData) < 1024 {
			return errors.New("SFFv2 需注入 1024 字节的 RGBA 色表")
		}
		f.Seek(36, io.SeekStart)
		var palOffset uint32
		binary.Read(f, binary.LittleEndian, &palOffset)
		if palOffset == 0 {
			return errors.New("SFFv2 不存在内置色表节点，无法覆盖")
		}

		f.Seek(int64(palOffset)+8, io.SeekStart)
		var dataOffset, dataLength uint32
		binary.Read(f, binary.LittleEndian, &dataOffset)
		binary.Read(f, binary.LittleEndian, &dataLength)

		if dataLength > 0 {
			f.Seek(int64(lofs+dataOffset), io.SeekStart)
			f.Write(actData[:dataLength])
			return nil
		}
		return errors.New("SFFv2 写入内置色表失败")
	}
}
