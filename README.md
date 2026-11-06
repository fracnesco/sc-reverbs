# Reverbs

**Four reverbs - a hall, a plate, a spring and a shimmer - each a UGen and an object with presets and a window, running anywhere a UGen does, and as inserts on SuperDirtGUI's mixer.** A SuperCollider extension.

```supercollider
s.boot;
Ndef(\keys, { Hall.ar(Blip.ar([220, 221], 8) * Decay2.kr(Impulse.kr(0.5), 0.01, 0.2) * 0.3, \hall) }).play;

h = Hall(Ndef(\keys), \hall);
h.gui;
h.preset = \cathedral;
h.decay = 6; h.damp = 3000;
```

| class | control | what it is |
|---|---|---|
| `Hall` | `\hall` | a concert hall: a modulated Dattorro tank, early reflections, a pre-delay; slow to build, long, moving |
| `Plate` | `\plate` | a plate: a feedback delay network, dense at once, bright, with a **hold** that freezes what is in it |
| `Coil` | `\coil` | a spring: chains of allpasses that chirp, up to three springs, a low pass and some saturation in the loop |
| `Shimmer` | `\shimmer` | a tank with a pitch shift in its feedback, so the tail climbs an octave (or whatever you choose) each round |

Every one of them lives in the same three places. Write `Hall.ar` into a graph of your own and drive it from the object, as above; give it a node of its own on a bus, `Hall.onBus(0, 2).enabled_(true)`; or put it in a chain, `FxChain.master.add(Hall())`. If SuperDirtGUI is installed, each is an insert on an orbit, a return, a group or the master: `~sdg.addInsert(0, \hall)`, `~sdg.addInsert(0, \plate)`, `\coil`, `\shimmer`.

In all four, `decay` is the time the tail takes to fall 60 dB, in seconds, whatever the size: the loop's gain is worked out from its lap. That is the one thing this extension has to get right, and it is measured (see Files).

They are built on [FxKit](../sc%20fx%20kit), which is built on [ParaEQ](../sc%20EQ), and the class library will not compile without them; the installer links both when the folders sit side by side. They use SuperCollider's own UGens, not sc3-plugins, and run on a stock server (64 wire buffers, 8 MB), one at a time or all four on one orbit.

Written for SuperCollider 3.13 on macOS. Nothing in it is platform specific apart from the symlink in `install.scd`, which Linux shares and Windows does not.

## Install

### As a quark

```supercollider
Quarks.install("https://github.com/fracnesco/sc-reverbs.git");
thisProcess.recompile;
```

The manifest names `sc-fx-kit` as a dependency, which names `sc-eq`, so `Quarks` fetches those first. The quark goes by the name of the repository, `sc-reverbs`; the classes inside it are `Hall`, `Plate`, `Coil` and `Shimmer`.

### From a clone

Clone `sc-eq` into a folder called `sc EQ`, `sc-fx-kit` into `sc fx kit` and this into `sc reverbs`, side by side. Open `install.scd` and evaluate its first block, then recompile the class library. It links what is missing and tells you what it did.

On Windows there is no `ln -s`, so use the quark, or copy the three folders into the extensions folder by hand: `Platform.userExtensionDir.openOS` opens it.

Install it one way or the other, **not both**: two copies on the class path is a duplicate class error at compile time.

### Check that it took

```supercollider
[Hall, Plate, Coil, Shimmer].collect(_.presetNames);
Plate.onBus(0, 2).enabled_(true).gui;
```

Select `Hall` and press cmd/ctrl + d for the help file, and see [examples/reverbs-demo.scd](examples/reverbs-demo.scd) for a walkthrough you can evaluate a block at a time.

## Hall

The input goes through a pre-delay, then four allpasses that smear it, into a tank of two branches in a figure of eight: each branch is an allpass whose time moves a little (`modDepth`, `modRate`), a delay, the damping, another allpass and a second delay, feeding the other branch. `room` scales every delay in it. `decay` is the time the tail takes to fall 60 dB whatever the room: the loop gain is worked out from the lap. `damp` is a low pass in the loop, so the high end dies first, as in a real room, and `lowCut` a high pass so the bottom does not pile up. Five early reflections a side, before the tail, at `early`.

The tank's first output arrives about 170 ms after the input at a room of 1, so with `early` at 0 a short sound has a gap before its tail; the reflections fill it.

Built in presets: default, small hall, concert hall, cathedral, dark hall, bright hall, chorus wash, infinite, drum room, far away.

| parameter | section | range | default | what it does |
|---|---|---|---|---|
| `preDelay` | space | 0 .. 0.25 s | 0.02 | the gap before the reverb starts |
| `room` | space | 0.3 .. 3 | 1 | how big the hall is: the tank's delays, scaled |
| `decay` | space | 0.2 .. 60 s | 2.5 | how long the tail takes to fall 60 dB |
| `diffusion` | space | 0 .. 1 | 0.7 | how smeared the input is on its way in: low is echoey, high is smooth |
| `early` | space | 0 .. 1 | 0.4 | how much of the first reflections, before the tail |
| `damp` | tone | 500 .. 20000 Hz | 6000 | the low pass in the loop: the high end dies first |
| `lowCut` | tone | 20 .. 1000 Hz | 60 | the high pass in the loop: keeps the bottom from piling up |
| `modDepth` | tone | 0 .. 1 | 0.3 | how much the tank's allpasses move: thickens the tail, chorus at the top |
| `modRate` | tone | 0.1 .. 3 Hz | 0.7 | how fast they move |
| `width` | tone | 0 .. 2 | 1 | how far apart the two sides are: 0 is mono |
| `mix` | tone | 0 .. 1 | 0.3 | how much of the effect goes out, against the dry signal |
| `output` | tone | -36 .. 24 dB | 0 | the level of the whole thing, in dB |

## Plate

A feedback delay network of four lines of 42 to 73 ms (scaled by `room`), Hadamard mixed, so the echoes are dense from the first moment, as on a steel plate; two allpasses a side smear the input on its way in (`diffusion`). `decay` is the time to 60 dB; `damp` and `lowCut` are the filters in the loop; `mod` moves the lines a little, which takes the metal out of a long tail.

`hold` freezes what is in it: the lines go round at unity, the filters step aside and nothing more comes in. A held plate never goes quiet, so in a SuperDirt insert an orbit with `hold` on does not sleep.

Built in presets: default, vocal plate, snare plate, bright plate, dark plate, short plate, long plate, tiny box, frozen, wide wash.

| parameter | section | range | default | what it does |
|---|---|---|---|---|
| `preDelay` | plate | 0 .. 0.1 s | 0.005 | the gap before the plate speaks |
| `room` | plate | 0.5 .. 2 | 1 | how big the plate is: the lines, scaled |
| `decay` | plate | 0.3 .. 30 s | 1.8 | how long the tail takes to fall 60 dB |
| `diffusion` | plate | 0 .. 1 | 0.6 | how smeared the input is on its way in |
| `hold` | plate | on / off | false | freeze what is in the plate: it rings on at unity, nothing more goes in |
| `damp` | tone | 1000 .. 20000 Hz | 9000 | the low pass in the loop: the high end dies first |
| `lowCut` | tone | 20 .. 500 Hz | 100 | the high pass in the loop |
| `mod` | tone | 0 .. 1 | 0.2 | how much the lines move: takes the metal out of a long tail |
| `width` | tone | 0 .. 2 | 1 | how far apart the two sides are: 0 is mono |
| `mix` | tone | 0 .. 1 | 0.3 | how much of the effect goes out, against the dry signal |
| `output` | tone | -36 .. 24 dB | 0 | the level of the whole thing, in dB |

## Coil

A spring is dispersive: the frequencies travel along it at different speeds, so a click comes out as a chirp, and the echoes of that chirp, round and round, are the boing. Here that is a chain of twelve allpasses at one short time, in a loop as long as the spring (`length`), through a low pass (`tone`: a spring carries nothing bright), a high pass and a little saturation (`drive`). `tension` sets how short each stage is - a tight spring chirps higher and faster - and `drip` how strongly it disperses. `springs` adds a second and a third spring of other lengths, each a loop of its own, so a hit smears across them.

`decay` is the time to 60 dB for the middle of the band; the low end, which the chain holds back longest, rings a little longer than that, as it does on a spring.

Built in presets: default, surf tank, guitar amp, short spring, long spring, dub tank, bright spring, loose, kicked, dry twang.

| parameter | section | range | default | what it does |
|---|---|---|---|---|
| `length` | spring | 0.02 .. 0.12 s | 0.045 | how long the spring is: the time round it, before the chirp |
| `decay` | spring | 0.2 .. 8 s | 2 | how long the tail takes to fall 60 dB |
| `tension` | spring | 0 .. 1 | 0.5 | how tight the spring is: tighter chirps higher and faster |
| `drip` | spring | 0 .. 1 | 0.6 | how strongly it disperses: the drip of a click, the boing of a hit |
| `springs` | spring | one two three | two | how many springs, of different lengths, in the tank |
| `tone` | tone | 800 .. 8000 Hz | 3500 | the low pass in the loop: a spring carries nothing bright |
| `lowCut` | tone | 40 .. 400 Hz | 120 | the high pass in the loop |
| `drive` | tone | 0 .. 1 | 0.1 | saturation in the loop: the tank's rattle on a hit |
| `width` | tone | 0 .. 2 | 1 | how far apart the two springs' sides are: 0 is mono |
| `mix` | tone | 0 .. 1 | 0.35 | how much of the effect goes out, against the dry signal |
| `output` | tone | -36 .. 24 dB | 0 | the level of the whole thing, in dB |

## Shimmer

A dense tank, and what comes out of it goes round again shifted by `interval` semitones (12 an octave up, 7 a fifth, -12 down), so a chord becomes a rising, glassy cloud: Eno and Lanois's trick. `feedback` is how much of the shifted tail goes round, `rise` how long each round takes, so how fast it climbs. `decay` is the tank's own time to 60 dB. The shift is a granular pitch shifter; `haze` spreads its grains so the climb is a wash rather than a staircase, and `detune` puts it a few cents off so it beats.

Past a feedback of about 0.9 with a long decay it keeps climbing until `damp` stops it: the low pass in the loop is what makes it come down, and `lowCut` the same for a shift downward.

Built in presets: default, octave up, fifth up, octave down, two octaves, slow climb, fast climb, detuned, glass, cloud.

| parameter | section | range | default | what it does |
|---|---|---|---|---|
| `preDelay` | shimmer | 0 .. 0.2 s | 0.01 | the gap before the tank speaks |
| `decay` | shimmer | 0.5 .. 20 s | 4 | the tank's time to fall 60 dB, before the feedback |
| `interval` | shimmer | -24 .. 24 st | 12 | the shift, in semitones: 12 an octave up, 7 a fifth, -12 down |
| `detune` | shimmer | -50 .. 50 ct | 0 | a few cents off the interval, so the climb beats |
| `feedback` | shimmer | 0 .. 1 | 0.6 | how much of the shifted tail goes round again |
| `rise` | shimmer | 0.05 .. 1 s | 0.25 | how long each round takes: how fast it climbs |
| `damp` | tone | 1000 .. 16000 Hz | 5000 | the low pass in the loop: where the climb stops |
| `lowCut` | tone | 60 .. 1000 Hz | 150 | the high pass in the loop: where a fall stops |
| `haze` | tone | 0 .. 1 | 0.3 | how spread the shifter's grains are: a wash rather than steps |
| `width` | tone | 0 .. 2 | 1 | how far apart the two sides are: 0 is mono |
| `mix` | tone | 0 .. 1 | 0.35 | how much of the effect goes out, against the dry signal |
| `output` | tone | -36 .. 24 dB | 0 | the level of the whole thing, in dB |

## The window

`h.gui` opens the top row (on, presets, save, copy, paste), the display, and a row of knobs per section. Hover over anything and the bar at the bottom says what it does. The display is the tail as it will fall: a gap for the pre-delay, the slope to 60 dB down over the decay, brighter the less damping there is. In the display, drag along for the decay and up and down for the damping.

## Presets

Every parameter is a property: `h.decay`, `h.decay = 4`. These also work by name, `h.set(\decay, 4)`, `h.get(\decay)`. Knobs clip to their range. The tables above are generated from the classes.

Yours go in `Platform.userAppSupportDir/hall-presets.scd` (and `plate-`, `coil-`, `shimmer-`): `h.saveAsPreset("mine")`, or type a name in the window and press save. `h.copySettings` and `other.pasteSettings` carry settings from one to another of the same class.

## Memory and sleep

Each instance takes a little of the server's real time memory for its loops: a hall 200 KB at stereo, a plate 150 KB, a coil 300 KB, a shimmer 400 KB. A stock server has 8 MB.

In a SuperDirt insert, an orbit sleeps when its effects fall silent; a reverb keeps it awake for as long as its tail sounds, and a held plate for ever.

## Files

| file | what it is |
|---|---|
| [classes/Hall.sc](classes/Hall.sc) | the hall: parameters, presets, the tank |
| [classes/Plate.sc](classes/Plate.sc) | the plate: parameters, presets, the input diffusion over the network |
| [classes/Coil.sc](classes/Coil.sc) | the spring: parameters, presets, the chains and their loops |
| [classes/Shimmer.sc](classes/Shimmer.sc) | the shimmer: parameters, presets, the shifted feedback round the network |
| [classes/ReverbKit.sc](classes/ReverbKit.sc) | what they share: the stereo pair, allpass and loop gain arithmetic, the feedback delay network |
| [classes/ReverbView.sc](classes/ReverbView.sc) | the window's display, the same for all four |
| [HelpSource/Classes/](HelpSource/Classes/) | a help file per class |
| [examples/reverbs-demo.scd](examples/reverbs-demo.scd) | a walkthrough, one block at a time |
| [install.scd](install.scd) | the symlink, a check, an uninstall |
| [sc-reverbs.quark](sc-reverbs.quark) | the quark manifest |

## Issues

Nobody has listened to these yet beyond the checks: every decay was measured on an impulse (a hall of 1 s measures 1.24, a plate 0.92, a spring 1.08), the shimmer's octave was found in its tail, the spring's chirp was measured as its bands arriving 7 ms apart - but whether they sound good is for your ears. The spring's `decay` holds for the middle of the band; its lows ring longer.

## License

MIT - see [LICENSE](LICENSE).
