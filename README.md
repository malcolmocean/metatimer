# MetaTimer

TL;DR: use this app to meditate. Start the timer, then when the timer ends, press the green button (or volume-up) to indicate that you successfully meditated the whole time, or the red button (or volume-down) to indicate that you got distracted while meditating.

## Warning

I haven't really touched this code in over a year (I don't even have an android development environment set up right now) so I don't know if it will even compile for you!

## Conceptual background

This is based on a model of practice which says that you want to spend most of your time *doing* the thing you're trying to practice, not not doing the thing. This comes from theory around [deliberate practice](http://malcolmocean.com/2013/06/flow-vs-deliberate-practice/). Most people, when they try to meditate, will set a timer for (say) 20 minutes. Then they sit down, and after a minute their mind wanders, and then at 8 minutes, they realize they've gotten distracted, and so they return their attention to their breath. Then they get distracted again, etc. In the whole 20 minutes, they spend maybe 3 minutes *actually being mindful*. (It's worth noting that the noticing process is itself a skill, and that maybe this app is not the best way to train that.)

The version here is actually designed based on an older model of how focus works than the one I have now (just a few months later... notably, after a conversation a last weekend with Val from [CFAR](http://rationality.org), who is a domain expert).

The key difference between models is that my old understanding didn't take breaks into account. It seems that focusing (which mindfulness is a kind of) is strenuous for the brain, which will then want to space out and engage in wandery thought. The kind of thinking you do in the shower. So Val's latest recommendation for practising meditation is to do it for as long as you can while remaining focused, then to rest your brain for a few moments (probably of at least the same length to the focusy time) before trying to focus again.

As mentioned, the APK presently attached isn't really designed for such breaks.

## How to use MetaTimer

What the current version of the app is designed for is mostly:

1. volume-button control (you can start and stop the timer using the volume buttons, meaning you don't have to look at the screen and since the ending signal is vibration you can even use it in your pocket)
2. gradually increasing time (valuable for reasons described above)
3. logs time, with failed times logged as 0.5

The way I would use the app with breaks in mind is that I'd probably use it normally--start with just 6s or so, and build up. Once you mess up, try again until the next messup. At that point, longpress on the number in the bottom right and it'll turn red. This means the timer is no longer recording (that feature was for debugging, so I don't confuse testing time with meditation time). Then, run the timer at whatever its current number is, and just relax your mind and think whatever thoughts you happen to have. I'd say probably run the timer in redmode while wander-thinking like 3 times, and then longpress on the number again again and resume trying to focus.

After doing this practice for however long (ballpark 5-30 mins) I'd suggest abstaining from doing intense mental work for the next at-least-as-long-possibly-longer, much like you wouldn't do a serious workout at the gym right before you go play sports. The optimal thing at this point might even be a powernap, which is extreeeemely non-focused :)

## Known bugs / needed features

Bugs:

- [ ] when you press the green button before the timer stops, it doesn't log the time

Feature ideas:

- [ ] allow the screen to be off, to save battery power
- [ ] custom parameters for eg. timer time increase/decrease
- [ ] display of time logged (the current version *does* save data to a database, but doesn't display it)
- [ ]  [beeminder](https://www.beeminder.com/api) integration
- [ ] ability to log different things separately (if you do multiple kinds of meditation)
- [ ] ability for app to prompt you to meditate throughout the day/week