/*
BitBlt.java
Copyright (c) 2008  Daniel H. H. Ingalls, Sun Microsystems, Inc.  All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/

package JSqueak.display.impl;

import java.awt.Rectangle;

import JSqueak.Squeak;
import JSqueak.vm.SqueakObject;
import JSqueak.vm.SqueakVM;
import JSqueak.vm.FormCache;

/**
 * @author Dan Ingalls
 *
 * Will eventually implement the full BitBlt plus Warp Drive(tm)
 */
public class BitBlt 
{
    private SqueakVM vm;
        
    private Object destForm;
    private FormCache dest;
    private int destX, destY, width, height;
    private int destIndex;
    private int destDelta;
    private int dstBitShift;

    private boolean noSource;
    private Object sourceForm;
    FormCache source;
    private int sourceX, sourceY;
    private int sourceIndex;
    private int sourceDelta;
    private int srcBitShift;
    private int sourceAlpha;

    private boolean noHalftone;
    private Object halftoneForm;
    private int[] halftoneBits;
    private int halftoneHeight;

    private boolean success;
    private boolean destIsDisplay;
    private int clipX, clipY, clipWidth, clipHeight;
    private boolean isWarping;
            private int combinationRule;
            private int bitCount;
    private int skew;
    private int mask1,mask2;
    private boolean preload;
    private int nWords;
    private int destMask;
    private int hDir,vDir;
    private int sx, sy;
    private int dx, dy;
    private int bbW, bbH;
    private int bitBltOop;
    private int affectedL, affectedR, affectedT, affectedB;
    private int opTable;
    private int ditherMatrix4x4;
    private int ditherThresholds16;
    private int ditherValues16;
    private int hasSurfaceLock;
    private int warpSrcShift;
    private int warpSrcMask;
    private int warpAlignShift;
    private int warpAlignMask;
    private int warpBitShiftTable;
    private int querySurfaceFn;
    private int lockSurfaceFn;
    private int unlockSurfaceFn;
    private int cmFlags;
    private int cmMask;
    private int[] cmShiftTable;
    private int[] cmMaskTable;
    private int[] cmLookupTable;
    private int cmBitsPerColor;
            
    final static int FN_XOR= 2;
    final static int FN_STORE_CONST= 12;
    final static int AllOnes= 0xFFFFFFFF;
    
    private final static int maskTable[]= {
        // Squeak's table for masking pixels within a word, based on depth'
        //  #(1 2 4 5 8 16 32) do:[:i| maskTable at: i put: (1 << i)-1].
        0x1, 0x3, 0, 0xF, 0x1F, 0, 0, 0xFF,
        0, 0, 0, 0, 0, 0, 0, 0xFFFF,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0xFFFFFFFF };
    
    public BitBlt(SqueakVM theVM) {
        vm= theVM;
        setDest(vm.newFormCache());
        source= vm.newFormCache();
    }
    
    public boolean loadBitBlt(SqueakObject bbObject, 
                       int argCount,  
                       boolean doWarp,
                       SqueakObject displayForm) {
        success= true;
        isWarping = doWarp;
        Object[] bbPointers= bbObject.getPointers();
        setCombinationRule(checkIntValue(bbPointers[3]));
        if (!success || (getCombinationRule() < 0) || (getCombinationRule() > (41 - 2))) 
            return false;
        if (getCombinationRule() >= 16 && getCombinationRule() <= 17) 
            return false;
        destForm = bbPointers[0];
        sourceForm = bbPointers[1];
        noSource = ignoreSourceOrHalftone(sourceForm);
        halftoneForm = bbPointers[2];
        noHalftone = ignoreSourceOrHalftone(halftoneForm);
        if (!getDest().loadFrom(destForm)) 
            return false;
        if (!loadBBDestRect(bbPointers)) 
            return false;
        if (!success) 
            return false;
        if (noSource) {
            sourceX = sourceY = 0;
        } else {
            if (!source.loadFrom(sourceForm)) 
                return false;
            if (!loadColorMap()) 
                return false;
            if ((cmFlags & 8) == 0) 
                setUpColorMasks();
            sourceX = checkIntOrFloatIfNil(bbPointers[8], 0);
            sourceY = checkIntOrFloatIfNil(bbPointers[9], 0); }
        if (!loadBBHalftoneForm(halftoneForm)) 
            return false;
        if (!loadBBClipRect(bbPointers)) 
            return false;
        if (!success) 
            return false;
        if (getCombinationRule() == 30 || getCombinationRule() == 31) {
            if (argCount != 1) 
                return false; // alpha arg is required
            sourceAlpha = checkIntValue(vm.top());
            if (!(sourceAlpha >= 0 && sourceAlpha <= 255)) 
                return false; 
            if (success) 
                vm.pop(); 
        }
        // Intersect incoming clipRect with destForm bounds
        if (clipX < 0) {
            clipWidth += clipX; clipX = 0; 
        }
        if (clipY < 0) {
            clipHeight += clipY; clipY = 0; 
        }
        if ((clipX + clipWidth) > getDest().getWidth()) {
            clipWidth = getDest().getWidth() - clipX; 
        }
        if ((clipY + clipHeight) > getDest().getHeight()) {
            clipHeight = getDest().getHeight() - clipY; 
        }
        destIsDisplay= destForm == displayForm;
        return true; 
    }
    
    boolean ignoreSourceOrHalftone(Object formPointer) {
        if (formPointer == vm.nilObj) 
            return true;
        if (getCombinationRule() == 0) 
            return true;
        if (getCombinationRule() == 5) 
            return true;
        if (getCombinationRule() == 10)
            return true;
        if (getCombinationRule() == 15) 
            return true;
        return false; 
    }
 
    int checkIntValue(Object obj) {
        if (SqueakVM.isSmallInt(obj))
			return SqueakVM.intFromSmall(((Integer)obj));
        success= false; 
        return 0; 
    }
    
    int checkIntOrFloatIfNil(Object intOrFloatObj, int valueIfNil) {
        double floatValue;
        if (SqueakVM.isSmallInt(intOrFloatObj))
			return SqueakVM.intFromSmall(((Integer)intOrFloatObj));
        if (intOrFloatObj == vm.nilObj) 
            return valueIfNil;
        SqueakObject floatObj= (SqueakObject) intOrFloatObj;
        if (floatObj.getSqClass()!=vm.getSpecialObject(Squeak.splOb_ClassFloat)) {
            success= false;
            return 0;
        }
        floatValue = floatObj.getFloatBits();
        if (!((-2.147483648e9 <= floatValue) && (floatValue <= 2.147483647e9))) {
            success= false; 
            return 0;
        }
        return ((int) floatValue); 
    }
    
    boolean loadBBHalftoneForm(Object aForm) { // Not done yet!! 
        if (noHalftone) 
            return true;
        if (SqueakVM.isSmallInt(aForm)) 
            return false;
        if (((SqueakObject)aForm).getFormat()<6) {
            //Old-style 32xN monochrome halftone Forms
            Object[] formPointers = ((SqueakObject)aForm).getPointers();
            if (formPointers == null || formPointers.length<4)  
                return false;
            halftoneHeight = checkIntValue(formPointers[2]);
            Object bitsObject = formPointers[0];
            halftoneBits = (int[])((SqueakObject)bitsObject).getBits();
            if (halftoneBits == null) 
                return false;
            if (!success || (halftoneHeight < 1)) 
                return false; 
        } else {
            //New spec accepts, basically, a word array
            if (((SqueakObject) aForm).getFormat() != 6)
                return false;
            halftoneBits = (int[]) ((SqueakObject) aForm).getBits();
            if (halftoneBits == null || halftoneBits.length < 1)
                return false;
            halftoneHeight = halftoneBits.length;
        }
        return true;
    }
    
    boolean loadBBDestRect(Object[] bbPointers) {
        destX = checkIntOrFloatIfNil(bbPointers[4], 0);
        destY = checkIntOrFloatIfNil(bbPointers[5], 0);
        width = checkIntOrFloatIfNil(bbPointers[6], getDest().getWidth());
        height = checkIntOrFloatIfNil(bbPointers[7], getDest().getHeight());
        return success;
    }

    boolean loadBBClipRect(Object[] bbPointers) {
        clipX = checkIntOrFloatIfNil(bbPointers[10], 0);
        clipY = checkIntOrFloatIfNil(bbPointers[11], 0);
        clipWidth = checkIntOrFloatIfNil(bbPointers[12], getDest().getWidth());
        clipHeight = checkIntOrFloatIfNil(bbPointers[13], getDest().getHeight());
        return success; 
    }
    
    boolean loadColorMap() {
        // Not yet implemented
        return true; 
    } 
    
    boolean setUpColorMasks() {
        // Not yet implemented
        return true; 
    } 
    
    void clipRange() {
        if (destX >= clipX) {
            sx = sourceX;
            dx = destX;
            bbW = width; 
        } else {
            sx = sourceX + (clipX - destX);
            bbW = width - (clipX - destX);
            dx = clipX; 
        }
        if ((dx + bbW) > (clipX + clipWidth)) {
            bbW -= (dx + bbW) - (clipX + clipWidth);
        }
        if (destY >= clipY) {
            sy = sourceY;
            dy = destY;
            bbH = height; 
        } else {
            sy = (sourceY + clipY) - destY;
            bbH = height - (clipY - destY);
            dy = clipY; 
        }
        if ((dy + bbH) > (clipY + clipHeight)) {
            bbH -= (dy + bbH) - (clipY + clipHeight);
        }
        if (noSource) 
            return;
        if (sx < 0) {
            dx -= sx;
            bbW += sx;
            sx = 0; 
        }
        if ((sx + bbW) > source.getWidth()) {
            bbW -= (sx + bbW) - source.getWidth();
        }
        if (sy < 0) {
            dy -= sy;
            bbH += sy;
            sy = 0; 
        }
        if ((sy + bbH) > source.getHeight()) {
            bbH -= (sy + bbH) - source.getHeight();
        }
    }
    
    public Rectangle copyBits() {
        // combines copyBits, copybitsLockedAndClipped, and performcopyLoop
        clipRange();
        if (bbW <= 0 || bbH <= 0) return null;
        destMaskAndPointerInit();
        setBitCount(0);
        /* Choose and perform the actual copy loop. */
        if (noSource) {
            copyLoopNoSource();
        } else {
            checkSourceOverlap();
            if ((source.getDepth() != getDest().getDepth()) || ((cmFlags != 0) || (source.isMsb() != getDest().isMsb()))) {
                copyLoopPixMap(); 
            } else {
                sourceSkewAndPointerInit();
                copyLoop(); 
            }
        }
        if (!destIsDisplay) 
            return null;
        if ((getCombinationRule() == 22) || (getCombinationRule() == 32)) 
            return null;
        if (hDir > 0) {
            affectedL = dx;
            affectedR = dx + bbW; 
        } else {
            affectedL = (dx - bbW) + 1;
            affectedR = dx + 1; 
        }
        if (vDir > 0) {
            affectedT = dy;
            affectedB = dy + bbH; 
        } else {
            affectedT = (dy - bbH) + 1;
            affectedB = dy + 1; 
        }
        return new Rectangle(affectedL, affectedT, affectedR-affectedL, affectedB-affectedT); 
    }
    
    void destMaskAndPointerInit() {
        int pixPerM1;
        int endBits;
        int startBits;
        pixPerM1 = getDest().getPixPerWord() - 1;  //Pix per word is power of two, so this makes a mask
        startBits = getDest().getPixPerWord() - (dx & pixPerM1); //how many pixels in first word
        mask1 = getDest().isMsb() ? AllOnes >>> (32 - (startBits * getDest().getDepth()))
                         : AllOnes << (32 - (startBits * getDest().getDepth()));
        endBits = (((dx + bbW) - 1) & pixPerM1) + 1;
        mask2 = getDest().isMsb() ? AllOnes << (32 - (endBits * getDest().getDepth()))
                         : AllOnes >>> (32 - (endBits * getDest().getDepth()));
        if (bbW < startBits) { 
            //start and end in same word, so merge masks
            mask1 = mask1 & mask2;
            mask2 = 0;
            nWords = 1; 
        } else {
            nWords = (((bbW - startBits) + pixPerM1) / getDest().getPixPerWord()) + 1;
        }
        hDir = vDir = 1; //defaults for no overlap with source
        destIndex = (dy * getDest().getPitch()) + (dx / getDest().getPixPerWord()); //both these in words, not bytes
        destDelta = (getDest().getPitch() * vDir) - (nWords * hDir); 
    }
    
    void checkSourceOverlap() {
        int t;
        if ((sourceForm == destForm) && (dy >= sy)) {
            if (dy > sy) {
                vDir = -1;
                sy = (sy + bbH) - 1;
                dy = (dy + bbH) - 1; 
            } else {
                if ((dy == sy) && (dx > sx)) {
                    hDir = -1;
                    sx = (sx + bbW) - 1; //start at right
                    dx = (dx + bbW) - 1;
                    if (nWords > 1) {
                        t = mask1; //and fix up masks
                        mask1 = mask2;
                        mask2 = t; 
                    }
                }
            }
            destIndex = (dy * getDest().getPitch()) + (dx / getDest().getPixPerWord()); //recompute since dx, dy change
            destDelta = (getDest().getPitch() * vDir) - (nWords * hDir); 
        }
    }
    
    void sourceSkewAndPointerInit() {
        int pixPerM1;
        int dxLowBits;
        int dWid;
        int sxLowBits;
        pixPerM1 = getDest().getPixPerWord() - 1;  //Pix per word is power of two, so this makes a mask
        sxLowBits = sx & pixPerM1;
        dxLowBits = dx & pixPerM1;
        // check if need to preload buffer
        // (i.e., two words of source needed for first word of destination)
        if (hDir > 0) {
            dWid = ((bbW < (getDest().getPixPerWord() - dxLowBits)) ? bbW : (getDest().getPixPerWord() - dxLowBits));
            preload = (sxLowBits + dWid) > pixPerM1; 
        } else {
            dWid = ((bbW < (dxLowBits + 1)) ? bbW : (dxLowBits + 1));
            preload = ((sxLowBits - dWid) + 1) < 0; 
        }
        skew = (source.isMsb()) ? (sxLowBits - dxLowBits) * getDest().getDepth()
                            :(dxLowBits - sxLowBits) * getDest().getDepth();
        if (preload) {
            if (skew < 0) 
                skew += 32;
            else 
                skew -= 32; 
        }
        /* calculate increments from end of one line to start of next */
        sourceIndex = (sy * source.getPitch()) + (sx / (32 / source.getDepth()));
        sourceDelta = (source.getPitch() * vDir) - (nWords * hDir);
        if (preload) 
            sourceDelta -= hDir; 
    }
    
    int halftoneAt(int index) {
        return halftoneBits[SqueakVM.mod(index,halftoneHeight)]; 
    }
    
    int srcLongAt(int index) {
        return source.getBits()[index]; 
    }
    
    int dstLongAt(int index) {
        return getDest().getBits()[index]; 
    }
    
    void dstLongAtput(int index, int intToPut) {
        getDest().getBits()[index]= intToPut; 
    }
    
    void copyLoopNoSource() {
        //  Faster copyLoop when source not used.  hDir and vDir are both
        //  positive, and perload and skew are unused
        int mergeWord;
        int word;
        int i;
        int halftoneWord;
        int destWord;
        
        halftoneWord = AllOnes;
        for (i = 1; i <= bbH; i += 1) { 
            // vertical loop
            if (!noHalftone) 
                halftoneWord = halftoneAt((dy + i) - 1);
            destMask = mask1; // First word in row is masked
            destWord = dstLongAt(destIndex);
            mergeWord = mergeFnwith(halftoneWord, destWord);
            destWord = (destMask & mergeWord) | (destWord & (~destMask));
            dstLongAtput(destIndex, destWord);
            destIndex ++;
            destMask = AllOnes;
            //The central horizontal loop requires no store masking */
            if (getCombinationRule() == 3) {
                destWord = halftoneWord;
                // Store rule requires no dest merging
                for (word = 2; word <= (nWords - 1); word += 1) {
                    dstLongAtput(destIndex, destWord);
                    destIndex ++; 
                }
            } else {
                for (word = 2; word <= (nWords - 1); word += 1) {
                    destWord = dstLongAt(destIndex);
                    mergeWord = mergeFnwith(halftoneWord, destWord);
                    dstLongAtput(destIndex, mergeWord);
                    destIndex ++; 
                }
            }
            if (nWords > 1) {
                //last word in row is masked
                destMask = mask2;
                destWord = dstLongAt(destIndex);
                mergeWord = mergeFnwith(halftoneWord, destWord);
                destWord = (destMask & mergeWord) | (destWord & (~destMask));
                dstLongAtput(destIndex, destWord);
                destIndex ++; 
            }
            destIndex += destDelta; 
        }
    }
    
    void copyLoop() {
        //This version of the inner loop assumes noSource = false.
        int mergeWord;
        int skewMask;
        int notSkewMask;
        int word;
        int prevWord;
        int unskew;
        int i;
        int halftoneWord;
        int skewWord;
        int y;
        int destWord;
        int hInc;
        int thisWord;
        int sourceLimit= source.getBits().length;
        hInc = hDir;
        if (skew == -32) {
            skew = unskew = skewMask = 0; 
        } else {
            if (skew < 0) {
                unskew = skew + 32;
                skewMask = AllOnes << (0 - skew); 
            } else {
                if (skew == 0) {
                    unskew = 0;
                    skewMask = AllOnes; 
                } else {
                    unskew = skew - 32;
                    skewMask = ( AllOnes) >>> skew; 
                }
            }
        }
        notSkewMask = ~skewMask;
        if (noHalftone) {
            halftoneWord = AllOnes;
            halftoneHeight = 0; 
        } else {
            halftoneWord = halftoneAt(0); 
        }
        y = dy;
        for (i = 1; i <= bbH; i += 1) {
            if (halftoneHeight > 1) {
                halftoneWord = halftoneAt(y);
                y += vDir; 
            }
            if (preload) {
                prevWord = srcLongAt(sourceIndex);
                sourceIndex += hInc; 
            } else {
                prevWord = 0; 
            }
            destMask = mask1;
            /* pick up next word */
            thisWord = srcLongAt(sourceIndex);
            sourceIndex += hInc;
            /* 32-bit rotate */
            skewWord = (((unskew < 0) ? ( (prevWord & notSkewMask) >>> -unskew) : ( (prevWord & notSkewMask) << unskew))) | (((skew < 0) ? ( (thisWord & skewMask) >>> -skew) : ( (thisWord & skewMask) << skew)));
            prevWord = thisWord;
            destWord = dstLongAt(destIndex);
            mergeWord = mergeFnwith(skewWord & halftoneWord, destWord);
            destWord = (destMask & mergeWord) | (destWord & (~destMask));
            dstLongAtput(destIndex, destWord);
            //The central horizontal loop requires no store masking */
            destIndex += hInc;
            destMask = AllOnes;
            if (getCombinationRule() == 3) {
                //Store mode avoids dest merge function
                if ((skew == 0) && (halftoneWord == AllOnes)) {
                    //Non-skewed with no halftone
                    if (hDir == -1) {
                        for (word = 2; word <= (nWords - 1); word += 1) {
                            thisWord = srcLongAt(sourceIndex);
                            sourceIndex += hInc;
                            dstLongAtput(destIndex, thisWord);
                            destIndex += hInc;
                        }
                    } else {
                        for (word = 2; word <= (nWords - 1); word += 1) {
                            dstLongAtput(destIndex, prevWord);
                            destIndex += hInc;
                            prevWord = srcLongAt(sourceIndex);
                            sourceIndex += hInc; 
                        }
                    }
                } else {
                    //skewed and/or halftoned
                    for (word = 2; word <= (nWords - 1); word += 1) {
                        thisWord = srcLongAt(sourceIndex);
                        sourceIndex += hInc;
                        /* 32-bit rotate */
                        skewWord = (((unskew < 0) ? ( (prevWord & notSkewMask) >>> -unskew) : ( (prevWord & notSkewMask) << unskew))) | (((skew < 0) ? ( (thisWord & skewMask) >>> -skew) : ( (thisWord & skewMask) << skew)));
                        prevWord = thisWord;
                        dstLongAtput(destIndex, skewWord & halftoneWord);
                        destIndex += hInc; 
                    }
                }
            } else {
                //Dest merging here...
                for (word = 2; word <= (nWords - 1); word += 1) {
                    thisWord = srcLongAt(sourceIndex); //pick up next word
                    sourceIndex += hInc;
                    /* 32-bit rotate */
                    skewWord = (((unskew < 0) ? ( (prevWord & notSkewMask) >>> -unskew) : ( (prevWord & notSkewMask) << unskew))) | (((skew < 0) ? ( (thisWord & skewMask) >>> -skew) : ( (thisWord & skewMask) << skew)));
                    prevWord = thisWord;
                    mergeWord = mergeFnwith(skewWord & halftoneWord, dstLongAt(destIndex));
                    dstLongAtput(destIndex, mergeWord);
                    destIndex += hInc; 
                }
            }
            if (nWords > 1) {
                // last word with masking and all
                destMask = mask2;
                if (sourceIndex>=0 && sourceIndex<sourceLimit) {
                    //NOTE: we are currently overrunning source bits in some cases
                    //this test makes up for it.
                    thisWord = srcLongAt(sourceIndex); //pick up next word
                }
                sourceIndex += hInc;
                /* 32-bit rotate */
                skewWord = (((unskew < 0) ? ((prevWord & notSkewMask) >>> -unskew) : ((prevWord & notSkewMask) << unskew))) | (((skew < 0) ? ( (thisWord & skewMask) >>> -skew) : ( (thisWord & skewMask) << skew)));
                destWord = dstLongAt(destIndex);
                mergeWord = mergeFnwith(skewWord & halftoneWord, destWord);
                destWord = (destMask & mergeWord) | (destWord & (~destMask));
                dstLongAtput(destIndex, destWord);
                destIndex += hInc; 
            }
            sourceIndex += sourceDelta;
            destIndex += destDelta; 
        }
    }

    void copyLoopPixMap() 
    {
        /*  This version of the inner loop maps source pixels
            to a destination form with different depth.  Because it is already
            unweildy, the loop is not unrolled as in the other versions.
            Preload, skew and skewMask are all overlooked, since pickSourcePixels
            delivers its destination word already properly aligned.
            Note that pickSourcePixels could be copied in-line at the top of
            the horizontal loop, and some of its inits moved out of the loop. */
        /*  The loop has been rewritten to use only one pickSourcePixels call.
            The idea is that the call itself could be inlined. If we decide not
            to inline pickSourcePixels we could optimize the loop instead. */
        int mergeWord;
        int i;
        int destPixMask;
        int nPix;
        int endBits;
        int nSourceIncs;
        int halftoneWord;
        int sourcePixMask;
        int skewWord;
        int words;
        int mapperFlags;
        int destWord;
        int dstShiftInc;
        int startBits;
        int dstShift;
        int srcShiftInc;
        int dstShiftLeft;
        int srcShift;
        int scrStartBits;
        source.setPixPerWord(32 / source.getDepth());
        sourcePixMask = maskTable[source.getDepth()];
        destPixMask = maskTable[getDest().getDepth()];
        mapperFlags = cmFlags & (~8);
        sourceIndex = (sy * source.getPitch()) + (sx / source.getPixPerWord());
        scrStartBits = source.getPixPerWord() - (sx & (source.getPixPerWord() - 1));
        nSourceIncs = (bbW < scrStartBits) ? 0 : ((bbW - scrStartBits) / source.getPixPerWord()) + 1;
        /* Note following two items were already calculated in destmask setup! */
        sourceDelta = source.getPitch() - nSourceIncs;
        startBits = getDest().getPixPerWord() - (dx & (getDest().getPixPerWord() - 1));
        endBits = (((dx + bbW) - 1) & (getDest().getPixPerWord() - 1)) + 1;
        if (bbW < startBits)
            startBits = bbW;
        srcShift = (sx & (source.getPixPerWord() - 1)) * source.getDepth();
        dstShift = (dx & (getDest().getPixPerWord() - 1)) * getDest().getDepth();
        srcShiftInc = source.getDepth();
        dstShiftInc = getDest().getDepth();
        dstShiftLeft = 0;
        if (source.isMsb()) {
            srcShift = (32 - source.getDepth()) - srcShift;
            srcShiftInc = 0 - srcShiftInc; 
        }
        if (getDest().isMsb()) {
            dstShift = (32 - getDest().getDepth()) - dstShift;
            dstShiftInc = 0 - dstShiftInc;
            dstShiftLeft = 32 - getDest().getDepth(); 
        }
        for (i = 1; i <= bbH; i += 1) {
            halftoneWord = (noHalftone) ? AllOnes : halftoneAt((dy + i) - 1);
            srcBitShift = srcShift;
            dstBitShift = dstShift;
            destMask = mask1;
            nPix = startBits;
            words = nWords;
            /* Here is the horizontal loop... */
            do {
                /* align next word to leftmost pixel */
                skewWord = pickSourcePixelsflagssrcMaskdestMasksrcShiftIncdstShiftInc(nPix, mapperFlags, sourcePixMask, destPixMask, srcShiftInc, dstShiftInc);
                dstBitShift = dstShiftLeft;
                if (destMask == AllOnes) {
                    mergeWord = mergeFnwith(skewWord & halftoneWord, dstLongAt(destIndex));
                    dstLongAtput(destIndex, destMask & mergeWord);
                } else {
                    destWord = dstLongAt(destIndex);
                    mergeWord = mergeFnwith(skewWord & halftoneWord, destWord & destMask);
                    destWord = (destMask & mergeWord) | (destWord & (~destMask));
                    dstLongAtput(destIndex, destWord); 
                }
                destIndex ++;
                if (words == 2) {
                    destMask = mask2;
                    nPix = endBits; 
                } else {
                    destMask = AllOnes;
                    nPix = getDest().getPixPerWord(); 
                }
            } while(!((words -= 1) == 0));
            sourceIndex += sourceDelta;
            destIndex += destDelta; 
        }
    }
    
    //    int pickSourcePixelsflagssrcMaskdestMasksrcShiftIncdstShiftInc(int nPix, int mapperFlags, int sourcePixMask, int destPixMask, int srcShiftInc, int dstShiftInc) {
    //                return 0; }  //dummy stub for now
    
    int pickSourcePixelsflagssrcMaskdestMasksrcShiftIncdstShiftInc(int nPixels, int mapperFlags, int srcMask, int dstMask, int srcShiftInc, int dstShiftInc)  {
        /*  Pick nPix pixels starting at srcBitIndex from the source, map by the
            color map, and justify them according to dstBitIndex in the resulting destWord. */
        int sourcePix;
        int nPix;
        int sourceWord;
        int destPix;
        int destWord;
        int dstShift;
        int srcShift;
        sourceWord = srcLongAt(sourceIndex);
        destWord = 0;
        srcShift = srcBitShift; //These two vars to get into registers -- needed in Java??
        dstShift = dstBitShift;
        nPix = nPixels;
        /* always > 0 so we can use do { } while(--nPix); */
        if (mapperFlags == (1 | 4)) {
            do {
                sourcePix = (sourceWord >>> srcShift) & srcMask;
                destPix = cmLookupTable[sourcePix & cmMask];
                /* adjust dest pix index */
                destWord = destWord | ((destPix & dstMask) << dstShift);
                /* adjust source pix index */
                dstShift += dstShiftInc;
                if (!(((srcShift += srcShiftInc) & AllOnes) == 0)) {
                    if (source.isMsb()) {
                        srcShift += 32; 
                    } else {
                        srcShift -= 32; 
                    }
                    sourceWord = srcLongAt(sourceIndex += 4); 
                }
            } while(!((nPix -= 1) == 0)); } /*clean double-neg here*/
        else {
            do {
                sourcePix = (sourceWord >>> srcShift) & srcMask;
                destPix = mapPixelflags(sourcePix, mapperFlags);
                /* adjust dest pix index */
                destWord = destWord | ((destPix & dstMask) << dstShift);
                /* adjust source pix index */
                dstShift += dstShiftInc;
                if (!(((srcShift += srcShiftInc) & AllOnes) == 0)) {
                    if (source.isMsb()) {
                        srcShift += 32; 
                    } else {
                        srcShift -= 32; 
                    }
                    sourceWord = srcLongAt(sourceIndex += 1); 
                }
            } while(!((nPix -= 1) == 0));  /*clean double-neg here*/
        }
        /* Store back */
        srcBitShift = srcShift;
        return destWord; 
    }

    /*  Color map the given source pixel. */
    
    int mapPixelflags(int sourcePixel, int mapperFlags) {
        int pv;
        pv = sourcePixel;
        if ((mapperFlags & 1) != 0) {
            if ((mapperFlags & 2) != 0) {
                /* avoid introducing transparency by color reduction */
                pv = rgbMapPixelflags(sourcePixel, mapperFlags);
                if ((pv == 0) && (sourcePixel != 0)) 
                {
                    pv = 1; 
                }
            }
            if ((mapperFlags & 4) != 0) {
                pv = cmLookupTable[pv & cmMask]; 
            }
        }
        return pv; 
    }
    
    int rgbMapPixelflags(int sourcePixel, int mapperFlags) {
        int val =     (cmShiftTable[0] < 0) ? (sourcePixel & cmMaskTable[0]) >>> -(cmShiftTable[0]) : (sourcePixel & cmMaskTable[0]) << (cmShiftTable[0]);
        val = val |  ((cmShiftTable[1] < 0) ? (sourcePixel & cmMaskTable[1]) >>> -(cmShiftTable[1]) : (sourcePixel & cmMaskTable[1]) << (cmShiftTable[1]));
        val = val |  ((cmShiftTable[2] < 0) ? (sourcePixel & cmMaskTable[2]) >>> -(cmShiftTable[2]) : (sourcePixel & cmMaskTable[2]) << (cmShiftTable[2]));
        return val | ((cmShiftTable[3] < 0) ? (sourcePixel & cmMaskTable[3]) >>> -(cmShiftTable[3]) : (sourcePixel & cmMaskTable[3]) << (cmShiftTable[3]));
    }


    int mergeFnwith(int sourceWord, int destinationWord) {
        switch (getCombinationRule()) {
            case 0: return 0;
            case 1: return sourceWord & destinationWord;
            case 2: return sourceWord & (~destinationWord);
            case 3: return sourceWord;
            case 4: return (~sourceWord) & destinationWord;
            case 5: return destinationWord;
            case 6: return sourceWord ^ destinationWord;
            case 7: return sourceWord | destinationWord;
            case 8: return (~sourceWord) & (~destinationWord);
            case 9: return (~sourceWord) ^ destinationWord;
            case 10: return ~destinationWord;
            case 11: return sourceWord | (~destinationWord);
            case 12: return ~sourceWord;
            case 13: return (~sourceWord) | destinationWord;
            case 14: return (~sourceWord) | (~destinationWord);
            case 15: return destinationWord;
            case 16: return destinationWord;
            case 17: return destinationWord;
            case 18: return sourceWord + destinationWord;
            case 19: return sourceWord - destinationWord;
            case 20: return sourceWord;
            case 21: return sourceWord;
            case 22: return sourceWord;
            case 23: return sourceWord;
            case 24: return sourceWord;
            case 25: { 
                if (sourceWord == 0)
                    return destinationWord;
                return sourceWord | (partitionedANDtonBitsnPartitions(~sourceWord, destinationWord, getDest().getDepth(), getDest().getPixPerWord())); 
            }
            case 26: return partitionedANDtonBitsnPartitions(~sourceWord, destinationWord, getDest().getDepth(), getDest().getPixPerWord());
            default: return sourceWord; 
        }
    }

    static int partitionedANDtonBitsnPartitions(int word1, int word2, int nBits, int nParts) {
        int i;
        int result;
        int mask;
        /* partition mask starts at the right */
        mask = maskTable[nBits];
        result = 0;
        for (i = 1; i <= nParts; i += 1) {
            if ((word1 & mask) == mask) {
                result = result | (word2 & mask); 
            }
            /* slide left to next partition */
            mask = mask << nBits; 
        }
        return result; 
    }

	public int getCombinationRule() {
		return combinationRule;
	}

	public void setCombinationRule(int combinationRule) {
		this.combinationRule = combinationRule;
	}

	public FormCache getDest() {
		return dest;
	}

	public void setDest(FormCache dest) {
		this.dest = dest;
	}

	public int getBitCount() {
		return bitCount;
	}

	public void setBitCount(int bitCount) {
		this.bitCount = bitCount;
	}
}
