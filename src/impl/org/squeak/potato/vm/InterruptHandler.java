/*
This work is a derivative of JSqueak (http://research.sun.com/projects/JSqueak).

Copyright (c) 2008  Daniel H. H. Ingalls, Sun Microsystems, Inc.  All rights reserved.

Portions copyright Frank Feinbube, Robert Wierschke.

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
package org.squeak.potato.vm;

import org.squeak.potato.objects.SmallInteger;
import org.squeak.potato.objects.SpecialObjects;
import org.squeak.potato.objects.SqueakObject;
import org.squeak.potato.primitives.PrimitiveHandler;

import static org.squeak.potato.objects.SpecialObjectConstants.*;

/**
 * @author Daniel Ingalls
 *
 * @author Frank Feinbube
 * @author Robert Wierschke
 */
public final  class InterruptHandler {

	public static int millisecondClockMask = SmallInteger.maxSmallInt >> 1; // keeps
																			// ms
																			// logic
																			// in
																			// small
																			// int
																			// range

	private int interruptCheckCounter;
	private int interruptCheckCounterFeedBackReset;
	private final int interruptChecksEveryNms;
	private int nextPollTick;
	public int nextWakeupTick;
	private int lastTick;
	private final int interruptKeycode;
	private boolean interruptPending;
	private final boolean semaphoresUseBufferA;
	private final int semaphoresToSignalCountA;
	private final int semaphoresToSignalCountB;
	private final boolean deferDisplayUpdates;
	private final int pendingFinalizationSignals;
	private PrimitiveHandler primHandler;

	// GG Default is 1000
	public static final int INTERRUPT_CHECK_COUNTER=1100;


	public InterruptHandler() {
		interruptCheckCounter = 0;
		interruptCheckCounterFeedBackReset = INTERRUPT_CHECK_COUNTER;
		interruptChecksEveryNms = 3; // GG WAS 3
		nextPollTick = 0;
		nextWakeupTick = 0;
		lastTick = 0;
		interruptKeycode = 2094; // "cmd-."

		interruptPending = false;
		semaphoresUseBufferA = true;
		semaphoresToSignalCountA = 0;
		semaphoresToSignalCountB = 0;
		deferDisplayUpdates = false;
		pendingFinalizationSignals = 0;
	}

	public void setPrimitiveHandler(PrimitiveHandler primHandler) {
		this.primHandler = primHandler;
	}

	/**
	 * GG Added final to hint some heavy optimization to JIT
	 */
	public final void checkForInterrupts() { // Check for interrupts at sends and
										// backward jumps

		SqueakObject sema;
		int now;
		if (interruptCheckCounter-- > 0) {
			return; // only really check every 100 times or so
			// Mask so same wrap as primitiveMillisecondClock

		}
		now = (int) (System.currentTimeMillis() & millisecondClockMask);
		if (now < lastTick) { // millisecond clock wrapped"

			nextPollTick = now + (nextPollTick - lastTick);
			if (nextWakeupTick != 0) {
				nextWakeupTick = now + (nextWakeupTick - lastTick);
			}
		}
		// Feedback logic attempts to keep interrupt response around 3ms...
		if ((now - lastTick) < interruptChecksEveryNms) // wrapping is not a
														// concern
		{
			interruptCheckCounterFeedBackReset += 10;
		} else {
			if (interruptCheckCounterFeedBackReset <= INTERRUPT_CHECK_COUNTER) {
				interruptCheckCounterFeedBackReset = INTERRUPT_CHECK_COUNTER;
			} else {
				interruptCheckCounterFeedBackReset -= 12;
			}
		}
		interruptCheckCounter = interruptCheckCounterFeedBackReset; // reset the
																	// interrupt
																	// check
																	// counter"

		lastTick = now; // used to detect wraparound of millisecond clock
		// if(signalLowSpace) {
		// signalLowSpace= false; //reset flag
		// sema= getSpecialObject(SpecialObjects.splOb_TheLowSpaceSemaphore);
		// if(sema != nilObj) synchronousSignal(sema); }
		// if(now >= nextPollTick) {
		// ioProcessEvents(); //sets interruptPending if interrupt key pressed
		// nextPollTick= now + 500; } //msecs to wait before next call to
		// ioProcessEvents"

		if (interruptPending) {
			interruptPending = false; // reset interrupt flag

			sema = SpecialObjects.getSpecialObject(splOb_TheInterruptSemaphore);
			if (sema != SpecialObjects.nilObj) {
				primHandler.synchronousSignal(sema);
			}
		}
		if ((nextWakeupTick != 0) && (now >= nextWakeupTick)) {
			nextWakeupTick = 0; // reset timer interrupt

			sema = SpecialObjects.getSpecialObject(splOb_TheTimerSemaphore);
			if (sema != SpecialObjects.nilObj) {
				primHandler.synchronousSignal(sema);
			}
		}
		// if(pendingFinalizationSignals > 0) { //signal any pending
		// finalizations
		// sema=
		// getSpecialObject(SpecialObjects.splOb_ThefinalizationSemaphore);
		// pendingFinalizationSignals= 0;
		// if(sema != nilObj) primHandler.synchronousSignal(sema); }
		// if((semaphoresToSignalCountA > 0) || (semaphoresToSignalCountB > 0))
		// {
		// signalExternalSemaphores(); } //signal all semaphores in
		// semaphoresToSignal
	}
}
