// =====================================================================
// ReverbKit - the arithmetic the four reverbs share
//
// Hall, Plate, Coil and Shimmer are each an FxParamUnit of their own. What
// they have in common is here, as class methods used inside their *process:
// a stereo pair out of any number of channels and back, an allpass's decay
// time for a coefficient, the gain of one lap of a loop for a decay in
// seconds, and the feedback delay network Plate and Shimmer are built on.
//
// Nothing here is an effect: SDGInsert and FxChainPanel list the subclasses
// of FxParamUnit, and this is not one.
// =====================================================================

ReverbKit {

	// the two channels a reverb works on: a mono input goes to both
	*pair { |sig|
		sig = sig.asArray;
		^[sig[0], sig[sig.size - 1]]
	}

	// the pair back onto n channels: one channel takes the middle of the two
	*spread { |lr, n|
		if (n == 1) { ^[(lr[0] + lr[1]) * 0.5] };
		^n.collect { |i| lr.wrapAt(i) }
	}

	// AllpassC's decay time for a delay and a coefficient: SC's allpass takes
	// the time of a 60 dB fall, and k ** (decay / delay) == 0.001 there
	*apTime { |delay, k|
		^delay * 3 / k.max(1e-3).log10.neg
	}

	// what one lap of `lap` seconds is scaled by, for a tail of `decay`
	// seconds to 60 dB
	*lapGain { |lap, decay|
		^10 ** (lap * -3 / decay.max(0.01))
	}

	// the 4 x 4 Hadamard, scaled so energy is kept
	*hadamard { |r|
		^[
			r[0] + r[1] + r[2] + r[3],
			r[0] - r[1] + r[2] - r[3],
			r[0] + r[1] - r[2] - r[3],
			r[0] - r[1] - r[2] + r[3]
		] * 0.5
	}

	// A feedback delay network of four lines, Hadamard mixed: dense, from a
	// plate's quick build to a long tail. in2 is a pair; room scales the
	// lines (1 is 42 to 73 ms); decay in seconds; damp and lowCut the filters
	// in the loop; mod moves the lines a little; hold 0..1 freezes it - the
	// lines go round at unity with the filters out, nothing more comes in.
	// Answers a pair. Up to maxRoom times the base lines of memory.
	*fdn { |in2, room, decay, damp, lowCut, mod = 0, hold = 0, maxRoom = 2|
		var base = #[0.0421, 0.0531, 0.0627, 0.0733];
		var rates = #[0.31, 0.47, 0.59, 0.73];
		var loop = FxLoop(4, (base.last * maxRoom) + 0.02);
		var times = base * room;
		var r = loop.read(times.collect { |t, i| t + (LFNoise2.kr(rates[i]) * mod * 0.0005) });
		var filtered = r.collect { |x| LPF.ar(HPF.ar(x, lowCut), damp) };
		var g = times.collect { |t| this.lapGain(t, decay) };
		var feed = [in2[0], in2[1], in2[0], in2[1]] * (1 - hold);
		r = r + ((filtered - r) * (1 - hold));
		g = g + ((1 - g) * hold);
		loop.write((this.hadamard(r) * g) + feed);
		^[(r[0] + r[2]) * 0.5, (r[1] + r[3]) * 0.5]
	}
}
