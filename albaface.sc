(
s.options.memSize = 8192*10;
s.waitForBoot({
	var mainWindow, userView, mainLayout;
	var possibleButtons = [], possibleSounds = [];
	var radioButtons = [];
	var soundSelector = [];

	MIDIdef.freeAll;
	MIDIClient.init;
	MIDIIn.connectAll;

	~bend = 8192;
	~bendSemitones = 1;
	~notes = Array.newClear(128);
	~modulationFreq=10;
	~modulationDepth=0.2;
	~attackTime=0.2;
	~glissandoTime=5;
	~selectedSound = \hoarsepad;
	~selectedMode = \gliss;
	~selectedModeIndex = 0;

	~clearNotes = {
		~notes.do({
			| n, i|
			n.set(\gate,0);
		});
		~notes = Array.newClear(128);
	};

	~polyButtonHandler = {

		MIDIdef.noteOn(\polyNoteOn, {
			| vel, nn, chan, src |
			~notes[nn] = Synth(~selectedSound,
				[   \freq, nn.midicps,
					\amp, vel.linexp(1,127,0.01,0.3),
					\gate, 1,
					\bend, ~bend,
					\modulation, ~modulation,
					\doneAction, Done.freeSelf
			]);
		});

		MIDIdef.noteOff(\polyNoteOff, {
			| vel, nn, chan, src |
			~notes[nn].set(\gate, 0);
			~notes[nn] = nil;
		});

		MIDIdef(\glissandoNoteOn).free;
		MIDIdef(\glissandoNoteOff).free;

		~selectedMode = \poly;
		~selectedModeIndex = 0;
		~clearNotes.value;
	};

	~glissandoButtonHandler = {

		MIDIdef.noteOn(\glissandoNoteOn, {
			| vel, nn, chan, src |
			if ((~notes[0].isNil), {
				~notes[0] = Synth(~selectedSound,
					[   \freq, nn.midicps,
						\amp, vel.linexp(1,127,0.01,0.3),
						\gate, 1,
						\bend, ~bend,
						\modulation, ~modulation,
						\mode, ~selectedMode,
						\glissandoTime, ~glissandoTime,
						\doneAction, Done.none
				]);
			}, /* else */ {
				~notes[0].set(
					\freq, nn.midicps,
					\amp, vel.linexp(1,127,0.01,0.3),
					\gate, 1,
					\bend, ~bend,
					\modulation, ~modulation,
					\mode, ~selectedMode,
					\glissandoTime, ~glissandoTime
				);
			});
		});

		MIDIdef.noteOff(\glissandoNoteOff, {
			| vel, nn, chan, src |
			//~notes[0].set(\gate, 0);
		});

		MIDIdef(\polyNoteOn).free;
		MIDIdef(\polyNoteOff).free;

		~selectedMode = \gliss;
		~selectedModeIndex = 1;
		~clearNotes.value;
	};


	SynthDef(\xfiles, {
		|out = 0, freq = 440, gate = 1, amp = 0.1, release = 0.2, mode=\poly, glissandoTime=1, doneAct=2 |
		var snd;
		var currentGlissandoTime = { | mode |
			if ((mode == \poly), {
				0;
			}, /* else */ {
				glissandoTime;
			});
		};
		var laggedFreq = Lag.kr(freq, currentGlissandoTime.value(mode));
		// whistle sound comes from narrow band pass on pink noise.
		// LFNoise2 is used as an LFO to add some waviness to the pitch.
		// super important for realism is a slight upward pitch slide on the onset (Line.kr)
		snd = BPF.ar(PinkNoise.ar, laggedFreq * (2 ** ({ LFNoise2.kr(6, 0.01) } ! 3 + Line.kr(-0.08, 0, 0.07))), 0.001) * 200;
		snd = Splay.ar(snd);
		snd = snd * EnvGen.ar(Env.adsr(0.03, 0.1, 0.9, release), gate, doneAction: doneAct);
		snd = Pan2.ar(snd, 0, amp);
		Out.ar(out, snd);
	}).add;

	SynthDef(\voice,{arg out=0,freq=440,gate=1,p=0,d=10,r=10, mode=\poly, glissandoTime=1, doneAct=2;
		var currentGlissandoTime = { | mode |
			if ((mode == \poly), {
				0;
			}, /* else */ {
				glissandoTime;
			});
		};
		var laggedFreq = Lag.kr(freq, currentGlissandoTime.value(mode));
		var sig=Array.fill(3,{|i| VarSaw.ar(laggedFreq*(i+1.0001),mul:0.05/(i+1))}).sum;
		var n=laggedFreq.cpsmidi;
		var sig2=Ringz.ar(WhiteNoise.ar(0.0003),TRand.ar(n,(n+1).midicps,Impulse.ar(10)));
		var env=EnvGen.kr(Env.linen(d,1,r),gate:gate,doneAction:doneAct);
		Out.ar(out,Pan2.ar((sig+sig2)*env*(0.8+SinOsc.kr(0.1,0,0.2)),p));
	}).add;

	SynthDef(\sound, {
		| out=0, freq=440, amp=0.3, gate=0, bend=0, modulation=8192, mode=\poly, glissandoTime = 1, doneAct=2 |
		var sig, env;
		var currentGlissandoTime = { | mode |
			if ((mode == \poly), {
				0;
			}, /* else */ {
				glissandoTime;
			});
		};
		var laggedFreq = Lag.kr(freq, currentGlissandoTime.value(mode));
		sig = SinOsc.kr(~modulationFreq,0,mul:(~modulationDepth*modulation)/64.0,add:1)*SinOsc.ar(laggedFreq* bend.linlin(0,16383,~bendSemitones.neg,~bendSemitones).midiratio);
		env = EnvGen.kr(Env.adsr(~attackTime), gate, doneAction:doneAct);
		sig = sig * env * amp;
		Out.ar(out, sig!2);
	}).add;


	SynthDef(\sound6, {
		| out=0, freq=55, amp=0.25, gate=0, bend=0, modulation=8192, mode=\poly, glissandoTime = 1, doneAct=2 |
		var currentGlissandoTime = { | mode |
			if ((mode == \poly), {
				0;
			}, /* else */ {
				glissandoTime;
			});
		};
		var laggedFreq = Lag.kr(freq, currentGlissandoTime.value(mode));
		var pct = 2.0;
		var mult = 2;
		var spectralfactor=0.5;
		var spectraldecay=SinOsc.kr(0.01).linexp(-1,1,4,0.98);
		var overtone = [ 0, 12, 19, 24, 28];
		var freqs = overtone.collect({ | el, i | laggedFreq*(el.midiratio) });
		amp= amp*3;

		a = 0;
		spectralfactor = 1;
		freqs.do({
			| freq, i |
			a = a + (spectralfactor*Formant.ar(
				freqs[i]+LFNoise1.ar(3,freqs[i]*pct/100.0),
				freqs[i]+LFNoise1.ar(3,freqs[i]*pct/100.0),
				freqs[i]*LFNoise1.ar(10,mult))).tanh;
			spectralfactor = spectralfactor / (spectraldecay**i);
		});
		a = amp*a*EnvGen.kr(Env.adsr(0.01,0.3,0.5,15,1,\cubed), gate, doneAction:doneAct);

		9.do{
			a=AllpassL.ar(a.tanh, 0.3, {0.1.rand+0.2}!2, 5);
		};

		Out.ar(out, a.tanh);

	}).add;

	SynthDef(\sound7, {
		| out=0, freq=55, amp=0.25, gate, bend=0, modulation=8192, mode=\poly, glissandoTime =1, doneAct=2 |
		var currentGlissandoTime = { | mode |
			if ((mode == \poly), {
				0;
			}, /* else */ {
				glissandoTime;
			});
		};
		var laggedFreq = Lag.kr(freq, currentGlissandoTime.value(mode));
		var pct = 2.0;
		var mult = 2;
		var spectralfactor=0.5;
		var spectraldecay=SinOsc.kr(0.01).linexp(-1,1,4,0.98);
		var overtone = [ 0, 12, 19, 24, 28];
		var freqs = overtone.collect({ | el, i | laggedFreq*(el.midiratio) });
		amp= amp*3;

		b = DynKlank.ar(`[[freqs, freqs*(12.midiratio), freqs*(19.midiratio)], nil, [1, 0.9, 0.6]], BrownNoise.ar(0.01))*EnvGen.ar(Env.adsr(0.01,0.3,0.5,15,1), gate, doneAction:doneAct);

		9.do{
			b=AllpassL.ar(b, 0.3, {0.1.rand+0.2}!2, 5);
		};

		Out.ar(out, b.tanh);

	}).add;


	SynthDef(\hoarsepad, {
		| out=0, freq=55, amp=0.25, gate=1, bend=0, modulation=8192, mode=\poly, glissandoTime = 1, doneAct=2 |
		var pct = 2.0;
		var mult = 2;
		var spectralfactor=0.5;
		var spectraldecay=SinOsc.kr(0.01).linexp(-1,1,4,0.98);
		var overtone = [ 0, 12, 19, 24, 28];
		var currentGlissandoTime = { | mode |
			if ((mode == \poly), {
				0;
			}, /* else */ {
				glissandoTime;
			});
		};
		var laggedFreq = Lag.kr(freq, currentGlissandoTime.value(mode));
		var freqs = overtone.collect({ | el, i | laggedFreq*(el.midiratio) });
		amp= amp*3;

		a = 0;
		spectralfactor = 1;
		freqs.do({
			| freq, i |
			a = a + (spectralfactor*Formant.ar(
				freqs[i]+LFNoise1.ar(3,freqs[i]*i*pct/100.0),
				freqs[i]+LFNoise1.ar(3,freqs[i]*i*pct/100.0),
				freqs[i]*LFNoise1.ar(10,mult))).tanh;
			spectralfactor = spectralfactor / (spectraldecay**i);
		});
		a = amp*a*EnvGen.kr(Env.adsr(0.01,0.3,0.5,15,1,\cubed), gate, doneAction:doneAct);
		b = DynKlank.ar(`[[freqs, freqs*(12.midiratio), freqs*(19.midiratio)], nil, [1, 0.9, 0.6]], BrownNoise.ar(0.01))*EnvGen.ar(Env.adsr(0.01,0.3,0.5,15,1), gate, doneAction:doneAct);

		9.do{
			a=AllpassL.ar(a.tanh, 0.3, {0.1.rand+0.2}!2, 5);
		};
		9.do{
			b=AllpassL.ar(b.tanh, 0.3, {0.1.rand+0.2}!2, 5);
		};

		c = [a, b];

		Out.ar(out, Select.ar(SinOsc.kr(0.2),c).tanh);

	}).add;

	s.sync;

	MIDIdef.bend(\testBend, {
		| val, chan, src |
		//[val, chan].postln;
		~bend = val;
		~notes.do({
			| synth |
			synth.set(\bend, ~bend);
		});
	}, chan:0);

	MIDIdef.cc(\modButton, {
		| vel, ccnum, chan, src |
		//[vel, ccnum, chan, src].postln;
		if ((ccnum == 1) , {
			~modulation = vel;
			~notes.do({
				| synth |
				synth.set(\modulation, ~modulation);
			})
		}, /* else */ {

		});

	},chan:0);

	mainWindow = Window.new.front;
	userView = UserView(mainWindow, Rect());

	possibleButtons =  [["Polyphonic", ~polyButtonHandler], ["Glissando", ~glissandoButtonHandler]];
	possibleButtons.do({ | el |
		radioButtons = radioButtons.add([
			Button.new(mainWindow,Rect()).states_([
				[el[0], Color.black, Color.white]
		]).action_(el[1])]);

	});

	possibleSounds = [["X-Files", "Hoarse Pad", "Sound", "Sound6", "Sound7", "Voice"], [\xfiles, \hoarsepad, \sound, \sound6, \sound7, \voice]];
	soundSelector = soundSelector.add([
		PopUpMenu(mainWindow, Rect()).items_(possibleSounds[0]).
		action_({ | ctrl | ~selectedSound = possibleSounds[1][ctrl.value]; possibleButtons[~selectedModeIndex][1].value; }),
	]);
	soundSelector = soundSelector.add([TextField(mainWindow, Rect()).string_(0.5).action_({|ctrl| ~glissandoTime = ctrl.value.asFloat.postln; possibleButtons[~selectedModeIndex][1].value; })]);

	~polyButtonHandler.value;

	mainLayout = GridLayout.rows(radioButtons, soundSelector);
	mainWindow.layout_(mainLayout);
});
)