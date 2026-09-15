This file lists major or notable changes to OpenPnP in chronological order. This is not
a complete change list, only those that may directly interest or affect users.

# Version 2.7

## New Features

* A Diagnostics page under Machine Setup measures what the machine actually does and writes the result out as a report. Issues and Solutions calibrates - it measures in order to set something - so it never reports the things no calibration step needs: the acceleration and velocity the controller really reaches as against the ones the axes are configured for, how far apart repeated approaches to one point land with compensation left exactly as configured, whether the machine follows a commanded step of one axis resolution at all or sticks and then breaks free, whether Units per Pixel is to scale across the whole field of view, when the camera image actually stops moving after a move, how far the origin wanders between homing cycles, and the play in the nozzle rotation. It also records the controller's own settings beside the ones OpenPnP plans with, since a controller limit below the axis setting caps every move without saying so. The seven groups are selected individually; the two that move nothing, the controller settings and the configuration snapshot, are the ones on by default. Nothing is changed in the configuration: backlash compensation is switched off for the measurements that have to see the machine's own behaviour and put back afterwards. The report, and a CSV per measurement series, go to a timestamped folder under the configuration directory.

* What the Diagnostics page measures now reaches Issues and Solutions, and the page says what machine it measured. Each test group keeps its conclusions in machine.xml as it finishes, and eight checks hold them against the settings they disagree with: a feed rate or an acceleration the controller will not allow, a limit the axis never reaches, a resolution finer than the smallest step the controller can make, a one-sided backlash offset that does not clear the backlash that was measured, a fixed camera settle time that ends before the image has stopped moving or that lasts several times longer than it needs to, and rotation backlash with no compensation set. Each offers the measured value, which can be adjusted before accepting, and accepting writes it into the setting. Two more report what was measured and name the calibration that already exists for it - visual homing where the origin wanders between homing cycles, the advanced camera calibration where Units per Pixel does not match what the camera sees across its field of view - rather than performing it a second time. Three further checks need nothing measured and are re-evaluated on every Find Issues: a nozzle tip run-out calibration that accepts as few as three measurements, circular symmetry detection left at whole-pixel accuracy, and a homing fiducial that is not where the calibration fiducial is. The page itself gained three sections above the measurement controls: a read-only overview of the axes, drivers, cameras and nozzles, where a row opens the element in the Machine Setup tree that edits it; the state of each calibration, from which fiducials are set through to what backlash compensation each axis has against the backlash it measured and when each test group last ran; and the list of what the measurements found, with Accept, Dismiss and Reopen.

* The main window was rearranged around the camera image. The eleven tabs across the top are now an icon rail down the left side: eleven titles had run out of width on a laptop, where the strip wrapped or clipped, and a count now belongs to the rail item rather than to its text - Issues and Solutions used to write an HTML span with a coloured dot into its own tab title, and carried a second copy of that title in every translation bundle to hold the markup. The machine controls and the coordinate readout moved onto the image itself, as cards in its lower corners, and the image takes half the window rather than what was left over beside a fixed column of controls; it is the one thing on screen that is worth more the larger it is. The readout is therefore no longer in the status bar. A wizard's instructions appear as a banner across the top of the image instead of pushing the camera aside while they are up. On a window too narrow for the readout and the controls to sit side by side, the readout moves above the controls rather than behind them. The multiple-window style now detaches only the camera: the machine controls travel with the image, so there is nothing left to put in a second window, and the preference keys that sized it are gone with it.

* The properties of whatever is selected are now shown in one column down the right hand side of the window, and the camera sits above the tables rather than beside them. Each page used to keep its own copy of the same arrangement - a table, its selection's property sheets underneath, and a divider between them - so there were nine dividers to drag, nine remembered positions, and a wizard that got whatever height was left at the bottom of the window. None of those panels said what they were showing, because the answer was always "whatever is selected above"; the column now names it and says what kind of thing it is. The sheets are tabs, one per property sheet, each keeping its own Apply and Reset. The column folds away with the button at its top right. Three pages keep their own divider, because their lower half is a second table rather than the properties of one thing: the placements of the selected board, the definition of the selected panel, and a panel's fiducials.

* Changing the selection with an edit you never applied now asks what to do about it everywhere. The feeders panel asked; the packages, parts, vision settings and machine setup pages silently discarded it, so changing the selected axis with a field typed but not applied lost the edit with no indication. The question itself was also hardcoded English, whatever the display language, because it was assembled by gluing the feeder's name onto " changed.  Apply changes?".

* Which camera is shown is a row of buttons over the top left of the image, where it was a full width drop-down above it. With the one or two cameras a machine usually has, every choice is visible and one click away rather than needing a click to open the list and a second to choose, and a strip of the camera area is no longer spent printing the name of the view underneath it. The buttons are labelled with the camera's own name, since the view says which head it is on. "Show All Horizontal" and "Show All Vertical" now read as "All, side by side" and "All, stacked", and those three choices are translated - they were hardcoded English whatever the display language.

* The jog controls were rearranged to fit on the camera image. The distance one press moves is five buttons showing the five distances, where it was a vertical slider standing as tall as the jog buttons beside it whose setting you read off whichever tick the handle was nearest. The speed is a horizontal slider with the percentage written next to it rather than a second vertical slider. The Special, Actuators and Safety tabs are gathered under a More fold at the bottom of the card, closed to begin with. Every keyboard shortcut is unchanged - Ctrl+Shift+F1 to F5 still pick a distance directly and Ctrl+Shift+Minus and Equals still step through them - the arrow keys still change the distance while the focus is on it, and Ctrl+Shift+J now folds the card away when you want the image to yourself. The result is about half the width of the panel it replaces.

* The window is drawn to the Pono mockups. The top bar carries the brand, the menus, the job's name with an unsaved dot, the machine state as a coloured pill, the job's Start, Pause, Step and Stop with their words on them, the job's progress, and the Ctrl+K search; the status bar reads the machine state, the last placement, the total and per-board progress, the remaining time, the units and the version. The buttons on the image are cards with the mockups' measurements: the camera selector, the scale, the reticle / grid / measure tools and zoom in the top row; the coordinate readout with the unit after each number; and the manual controls as one card - the tool at the top with Home and a fold beside it, the X/Y pad with the Z and C columns and their park keys, the step as five segments, the speed as a slider with its percentage, and Park XY, Park Z, Safe Z, Discard and Recycle along its foot. The actuators and the board protection switch, which the mockups leave off the card, live behind its "..." button. A wizard's instructions are a banner with the accent rule down its left and Next in the accent. The theme follows the mockups' colour tokens in both light and dark, and the icons are the mockups' own set.

* The tables area of a page is one rounded card with a row of tabs along the top - an icon, a name and a count each - and under the selected tab a toolbar with words on its buttons and a filter box at the right, over a table of 32 pixel rows with a muted header and a 3 pixel accent bar down the left of a selected row. The toolbars used to be rows of icons with no words, which is why the wiki has a page explaining what each one does. On the job page the boards and the placements are two tabs rather than two tables stacked behind a divider, with the running log as a third and a chip saying which board the placements belong to; the feeders page gains a "needs attention" tab showing only the feeders that are switched off or have no part, and a scope switch for all / enabled / used by this job. The cells use the mockups' shapes: the accent check for a placement's enabled flag, the toggle for a feeder's, the T or B badge for a side.

* Selecting a placement puts a form in the properties column: position, part, placement options, this run and notes, each a section with a heading that folds. The package and the feeder follow the part chosen before it is applied, and the feeder reads "ready" when an enabled feeder carries the part and "none" in red when nothing does. The table used to be the only place a placement could be edited, one cell at a time. The column's header shows the icon in an accent square with the name over the type, and one Reset and Apply at its foot stand in for the pair every sheet used to carry; it lights up while anything on show has an edit to apply. A lone sheet shows without a tab strip.

* The diagnostics read every position through several frames of the camera rather than one, and say how much the camera scatters on its own. Each standing position is the median of five frames, and the scatter of the frames goes into the CSV beside it, with the time and the detection score; a new Vision noise floor group locates the fiducial in thirty frames with nothing moving and reports the spread, which is the floor under everything else the diagnostics measure - a machine cannot be shown to repeat better than the camera can see, and the repeatability, backlash and homing findings now say how many times the floor they are. A camera scattering by more than half a pixel is raised as an issue pointing at the vision solutions. The sample sizes were too small to mean what the findings said: ten repeats rather than five, three approach pairs per cell of the backlash matrix rather than one, five homing cycles rather than three, three runs per settle distance rather than one, and the rotation test writes every raw angle rather than only the means. The defaults change for new configurations only; a machine.xml that already holds the old counts keeps them until they are edited on the page.

* Two diagnostics groups for what the timing could not see. Lost steps: each axis makes a run of long fast moves at each speed factor - 20 cycles of 100 mm by default at 0.25, 0.5, 0.75 and 1.0 of the feed rate - and is then brought back to the fiducial from the same side as before; whatever it is off by is what the run lost, per 1000 mm of travel, and the controller's position count does not know. An axis that loses steps is raised as an issue offering the feed rate scaled to the highest factor that came back clean. Camera latency: frames are taken continuously through a move slow enough that nothing vibrates afterwards, and the frames that still show the move after the controller has reported it complete are the delay of the camera's pipeline, in milliseconds and in frames. A fixed settle wait shorter than that delay is raised as an issue, until the settle group has been run and carries the same conclusion with the better number.

* The camera settling group now arrives along X, along Y and diagonally, several times each, and reads the oscillation off every arrival: amplitude, the frequency the frame rate could resolve - stated as such, since a 40 Hz belt resonance seen at 30 frames a second reads as 10 Hz and the samples cannot tell the two apart - and the time constant of the decay, with a warning where the image takes more than 300 ms to die down. A new Z focus group measures the one axis the down-looking camera cannot see: the nozzle tip is swept through the bottom camera's focus from above and from below, five times each, and where the image is sharpest is where Z really stopped; the spread from one side is the Z repeatability, the difference between the sides is the slack, and slack over 0.05 mm with no compensation covering it is raised as an issue offering directional compensation by the amount measured. Most Z axes ship with directional compensation and an offset of zero, which is none.

* A Datum board group measures the machine against geometry that is known, which nothing before it could: every other test measures the machine against itself. The LumenPnP datum board that carries the primary fiducial has six more copper fiducials around it, placed by one photoplot to about 0.02 mm, a 30 mm copper ruler, and a pair of 5 mm discs. Starting from the primary fiducial the group works out which of the eight ways the board lies, finds all seven fiducials, and fits the machine's frame to them: how long a machine millimetre is along X and along Y, how far the Y axis leans from square, and what does not fit a straight frame at all. A scale error is raised as an issue naming the corrected steps per millimetre where the firmware group reported the current ones; axes out of square are raised with the factor for a linear transformed axis. Neither is written from here. The ruler is read twice: in one frame, the tick spacing against the 1.000 mm the copper says gives the camera's own scale with the machine standing still - the field of view scan measures the camera against the machine's moves and cannot separate the two - and then the camera steps along it in quarter millimetres, reading the position error at a resolution fine enough to see the 2 mm pitch of a GT2 belt. The discs report how far the solder mask and the silkscreen are registered from the copper on this particular board, which is why the board's grid is not used for anything precise.

* The diagnostics page leads with what to run. The test groups, with Run, Stop and Open report, were the seventh section of the page, under four overview tables, the calibration status and the issue table, which is where nobody found them; they are the first now, in the order they run, and each says when it last ran and how long it took.

* Machine diagnostics are a page of their own on the navigation rail, between Machine and Issues. They were a property sheet of the machine node, which put three tables of measurements in the 500 pixel properties column, where they had been laid out for the width of the window.

* The advanced motion planner can fuse the final approach of a two-part backlash compensation into the move that follows it, under Machine Setup / Motion Planner. One-sided positioning, and sneaking up, drive past the target and then approach it in a second move of their own, which costs a still-stand and a line of G-code every time an axis moves - three times per placement on a typical job. Where the move that follows does not touch the compensated axes, which is what the Z descent onto a part looks like, the two are planned as one coordinated line: the compensating travel hides inside the descent, and the axis arrives from the same side as before and more gently, because it now covers its fraction of a millimetre over the whole descent. Off by default, and it needs continuous motion to be allowed, since there is nothing to fuse into if the planner waits for each move to complete. Leave it off if a nozzle has to descend into a changer slot or another place too narrow for the backlash offset of lateral travel, because the fused move carries that travel through the descent.

## Security

* The ScriptRun vision pipeline stage now only runs scripts located inside the OpenPnP scripts directory. Pipelines are routinely shared and pasted between users, and the stage previously ran whatever script path the pipeline named - including one on a network share - with no confirmation, and did so unattended once the pipeline was assigned to a feeder. If you use ScriptRun, move your script into the scripts directory and update the stage's file setting. A path relative to the scripts directory is now also accepted.

## Bug Fixes

* The "Speed over precision" motion option now also applies to the sneak-up backlash compensation. The option exists to do without the extra moves that compensation causes, and it was honoured by the one-sided methods and silently ignored by directional sneak-up - the one method that always adds a move. A move made with that option now applies the offset in one go, arriving from the same side as before but without the slow final segment, which is how directional compensation has always behaved.
* The sneak-up offset of a rotational axis is no longer read in a different unit from the backlash offset it is subtracted from. Both are angles, and an angle must not be scaled by a unit conversion; the backlash offset was already handled that way and the sneak-up offset beside it was not. This changes nothing on a machine whose system units are millimetres, which is the usual case. If your system units are inches or thou and you have a rotational axis using directional sneak-up, check its sneak-up offset after upgrading: the number you see is unchanged, but it is now used as the angle it reads as.
* Errors that were previously printed to the console, or discarded entirely, are now reported through the log with the context needed to act on them. Where an exception really is expected, the reason is recorded in the code instead.
* A vision pipeline now stops at the stage where a machine level error occurred, such as a failed image capture or actuation. Previously it ran all the remaining stages first, which produced a cascade of misleading follow-on errors before reporting the real one.
* Length fields now accept the written unit names - "in", "inch", "inches", "ft", "feet", "thou", "millimeters", "micrometer" and so on - alongside the prime marks used for display. Previously only `"` and `'` were recognised for inches and feet, and a suffix that was not recognised was discarded silently, so typing `15in` into a field that positions the machine gave you 15 mm. A suffix that still is not a unit is now reported instead of dropped, which also means `1e5` is refused rather than read as 1.
* The camera reticle you select is now stored in the same XML format as the rest of the configuration. Two consequences: the "filled" option of the fiducial reticle is remembered, where before it was silently dropped on every restart, and a reticle stored by an earlier version cannot be read, so the first time you run this version you will need to pick your reticle once more.
* The crosshair reticle's colour menu no longer offers Red twice. The second entry was in the same button group as the first and bound to the same colour, so selecting it moved the mark but changed nothing. Found while translating the menu into Italian.
* KiCad position files that name the side by its copper layer - `B.Cu` rather than `bottom`, which is what KiCad wrote around 2014 and what some export scripts still write - now get their bottom side mirrored like any other. Previously only the word `bottom` was recognised, so those files were imported unmirrored and every part on the bottom side ended up in the wrong place. The side column is also matched without regard to case now.
* Converting a machine coordinate back into a placement coordinate now reports a clear error when the board's placement transform cannot be inverted. Previously the failure was only logged and the calculation carried on with the uninverted transform, which produced a plausible looking but wrong coordinate. A transform only becomes non-invertible if the fiducial measurements it was derived from were degenerate, so the remedy is to re-run the fiducial check for that board.
* The X, Y and Z fields of the location buttons now honour the unit you type. Previously the unit was parsed and then discarded, so `1"` was applied as 1 mm. An unparseable coordinate is also reported now, rather than being treated as zero and moving the machine there.

## Translations

* The Diagnostics page is translated into Simplified Chinese, including everything it reports: its own labels and columns, the wording of the issues it raises, and the explanations it assembles around the measured numbers. The other five languages fall back to English there until a translation round picks the new strings up.
* Around 630 labels, buttons, checkboxes, tooltips and panel titles that were hardcoded English in the source have been moved into the translation bundle, so they can be translated at all. Among them are the long explanatory tooltips of the push-pull, blinds and Bamboo feeders, the interlock actuator and the contact probe nozzle, which are the most worth translating and were the last to still be unreachable. They are still English until each language picks them up, but a translator can now reach them. Most were in the feeder and machine configuration wizards. A handful of strings were deliberately left alone because translating them would be wrong: the file names on the diagnostics dialog, the copyright notices, and a hidden string that only exists to size a field.
* The View / Language menu is now built from the translation files that are actually present, instead of from a list in the source. Adding a language is therefore just a matter of adding its `translations_<code>.properties` file - no code change, and no new build - which also means a language someone is still working on shows up as soon as the file exists.
* On a fresh installation OpenPnP now starts in your system language if a translation for it exists, falling back to English otherwise. Previously it always started in English regardless, despite the documentation saying otherwise. This only applies when no language has ever been chosen: if you have picked one, including English, that choice is kept. If you have been running in English without ever visiting the language menu, this version will switch to your system language, and you can set it back under View / Language.
* The Issues and Solutions descriptions are now translated, 91 of them into Simplified Chinese. These are written in the source as English prose rather than as translation keys, because that English wording is also how OpenPnP remembers which issues you dismissed or marked as solved. It therefore stays as written and the translation is looked up from it, which means your dismissed and solved issues survive this change and no longer shift when you change display language. Descriptions that are assembled at runtime from machine, axis or nozzle names still appear in English, as does anything not yet translated.
* The error and confirmation dialogs of the parts, packages, feeders and vision settings pages, the main window's configuration save and load failures, the pipeline editor, the diagnostics submission, the wizard validation message, and the DipTrace and Labcenter Proteus import failures are now translated. They were hardcoded English regardless of the display language. The DipTrace import failure also now names the reason before the format description rather than only after it.
* Adding an actuator that fails now says "Actuator Error" rather than "Camera Error", which is what the dialog had been titled.
* Every error and confirmation dialog in the interface is now translated. The last of them were the camera calibration wizard's six prerequisite checks, the push-pull feeder's auto setup, clone and OCR report dialogs, the heap and tray feeder validation messages, the bank deletion messages of the slot feeders, and the three "you will need to recapture / recalibrate" warnings about overwriting a Z reference. Deleting an axis also now names the axis in the prompt rather than only in the title.
* The "do you want to move there first?" prompt that precedes opening a pipeline editor is now translated, along with the four descriptions it quotes and the two pipeline editor titles. So is the multi-placement board location result, which reports the scaling, shearing and origin offset it measured; its advice also no longer misspells "remedies".
* The wizard dialogs are now translated too: the G-code driver's export, import, copy, paste and reset-to-defaults dialogs, the bottom and fiducial vision settings' reset and pipeline-replacement confirmations, the camera calibration completion prompt, the template-image selection error shared by the drag, lever and NeoDen 4 feeders, the strip feeder's auto setup failure, the push-pull and Bamboo feeders' feed count reset, the BlindsFeeder's apply-to-all confirmations, the package pad dialogs, the pipeline editor's unsaved-changes prompt, and the G-code console's machine-not-started notice. The two vision settings reset prompts also no longer read "reset the settings with to the default settings" or end in a double question mark.
* German, Spanish, French, Italian and Russian are now complete translations. They were not: German had 112 of the interface's strings, Italian 73, French 76 and Spanish 66, out of 2650, so choosing one of those languages changed a handful of menu items and left the rest of the program in English. Russian was further along at 1683 but still missing a third. All five now cover every translatable string, and all five have the Issues and Solutions descriptions, which previously existed only in Simplified Chinese.
* Translating the interface end to end turned up faults in the English it was translated from, which are fixed. The first measurement button in the camera wizard said "Measure" both before and after you pressed it, giving no sign that the second press confirms rather than re-measures. A checkbox in the camera vision panel had its own tooltip sentence sitting where its label should be. And the file open dialog for existing boards and panels built its sentence and its file filter by gluing the translated word for "board" or "panel" into the middle of them, which produced ungrammatical prompts in Russian and Italian and, worse, a filter looking for files ending `.单板.xml` in Chinese, so the dialog listed nothing.
* Three Simplified Chinese labels said the wrong thing, and one of them mattered. On the auto feeder's actuator panel, the row that selects what runs *after* a pick was labelled "feed early", which is the opposite operation - and "pre pick" genuinely exists elsewhere in the program under its own correct label, so there was nothing on screen to suggest the row had been mislabelled rather than that the feature worked that way. The row above it had picked up an invented qualifier to pair with the mistake. Separately, the OpenPnpCapture camera panel called itself "device" on its border and "camera name" on the field inside it.
* Simplified Chinese wording is now consistent where the English is one word. Sixty-nine English labels were being rendered more than one way - "Feed" split roughly evenly between two different words, to the point where two adjacent retry-count fields disagreed; parenthesised English glosses used full-width brackets in twenty-eight places and half-width in sixty-seven; and two labels had smuggled a hardcoded font and a hardcoded red into their translated text. Twenty-six differences are deliberate and were kept: where English uses one word for two different things, such as Start meaning both "start the job" and "switch the machine on", or Reticle meaning both the camera crosshair and the board viewer's grid, two renderings is the correct answer.
* Forty-six translation keys belonging to classes that no longer exist have been removed from all seven language files. Nothing displayed them, but they made the tooling report differences between panels that a user could never see, and in one case they made a correct translation look like a mistake.
* Simplified Chinese now has an entry for every translatable string in the interface. 19 had none at all and appeared in English: the Rank column and the explanation of how ranks order a job, the job processor's placement attempt limits and feeder fault settings, the Photon feeder's "Feed 1mm" button and its move-while-feeding option, and the Priority and Faults columns on the Feeders page. Another 12, mostly the Rotation coordinate labels, were present but left in English. Terms the translation deliberately keeps in English, such as Safe Z, Camera Z, TCP and Gcode, are unchanged.

# Version 2.6

## New Features

* The [Rank](https://github.com/openpnp/openpnp/wiki/Rank) feature, supporting the "how do I make sure X is placed before Y?" requirement. [PR 1842](https://github.com/openpnp/openpnp/pull/1842)
* Job planner improvements which improve throughput [PR 1857](https://github.com/openpnp/openpnp/pull/1857)
* Changes for Photon feeder:
    * Speed up feeding by moving while feeding. NB this is enabled by default. [PR 1843](https://github.com/openpnp/openpnp/pull/1843) [PR 1903](https://github.com/openpnp/openpnp/pull/1903) [PR 1929](https://github.com/openpnp/openpnp/pull/1929)
    * Added "Skip Next Feed" and "Disable Feed" feeder options and Recycle support [PR 1900](https://github.com/openpnp/openpnp/pull/1900)
    * Added "Feed 1mm" button [PR 1913](https://github.com/openpnp/openpnp/pull/1913)
* Many translation improvements. [PR 1871](https://github.com/openpnp/openpnp/pull/1871)
* Update the outdated ReferenceStripFeeder default vision pipeline. It now works the same as all the other sprocket-hole vision pipelines. [PR 1841](https://github.com/openpnp/openpnp/pull/1841)
* The "Discard" button now always performs the discard action, even if openpnp thinks the nozzle is already empty. [PR 1890](https://github.com/openpnp/openpnp/pull/1890)
* Retries of the full pick/vision/place cycle for parts that fail vision check, or have some other problem during that cycle. [PR 1898](https://github.com/openpnp/openpnp/pull/1898)
* Each feeder records a tally of whether its parts led to successful placements, or have problems such as failing the vision check. The default configuration is for a feeder to get disabled if it fails 3 out of 6 placements. This tally is shown in a new column on the Feeders page. [PR 1898](https://github.com/openpnp/openpnp/pull/1898)
* Feeders have a new Priority field (Low/Normal/High). It picks from the highest priority if there are multiple feeders enabled for one part. This is for using up the tail end of an old tape, and having the machine automatically swap over to the new tape when empty. [PR 1898](https://github.com/openpnp/openpnp/pull/1898) [PR 1922](https://github.com/openpnp/openpnp/pull/1922)
* If there are multiple feeders (for one part) at the same priority it will now use the closest. [PR 1898](https://github.com/openpnp/openpnp/pull/1898)
* If camera lighting is configured to be turned off after a capture, openpnp will now keep the light on for the duration of a batch of captures, for example during part alignment, or board fiducial scan. [PR 1915](https://github.com/openpnp/openpnp/pull/1915)
* Some keyboard shortcuts used to move the machine are also commonly used for text editing. Prevent accidental movement by block the machine movement action if a text edit field is focussed. [PR1928](https://github.com/openpnp/openpnp/pull/1928)
* ReferencePushPullFeeder - an optional delay at each step of the movement [PR 1934](https://github.com/openpnp/openpnp/pull/1934)
* Changes for scripting:
    * A new "Job.Error" script. [PR 1889](https://github.com/openpnp/openpnp/pull/1889)
    * A new "Feeder.Fault" script. [PR 1898](https://github.com/openpnp/openpnp/pull/1898)
    * Previously script events were run if the filename matches 'EventName.py'. Change this to also run 'EventName.YourTextInHere.py' etc [PR 1895](https://github.com/openpnp/openpnp/pull/1895)

## Bug Fixes

* Fix the nozzle rotation mode "Minimal Rotation" [PR 1883](https://github.com/openpnp/openpnp/pull/1883)
* Fix bug where the job processor might pick up a part when there is already another part on the nozzle [PR 1870](https://github.com/openpnp/openpnp/pull/1870)
* Fix possible lock up in GcodeAsyncDriver [PR 1856](https://github.com/openpnp/openpnp/pull/1856)
* Fix [issue 1884](https://github.com/openpnp/openpnp/pull/1884) where a feeder that became disabled in the middle of a job might show an unhelpful error message `Cannot invoke "org.openpnp.model.Location.convertToUnits(org.openpnp.model.LengthUnit)" because "b" is null`. 
[PR 1886](https://github.com/openpnp/openpnp/pull/1886)
* Fix copy/paste of a Part not copying the Package setting, or vision pipeline choices. [PR1907](https://github.com/openpnp/openpnp/pull/1907)
* Fix an error message when deleting a Part [PR1906](https://github.com/openpnp/openpnp/pull/1906)
* Fix the last placement in a job having a longer than expected dwell time. [PR1905](https://github.com/openpnp/openpnp/pull/1905)
* Fix some vision pipeline stages that could be used to mask 100% of the image which were previously leaving a stray unmasked pixel. [PR1910](https://github.com/openpnp/openpnp/pull/1910)
* Fix bugs handling tray feeders configured with fewer than 1 row or column. [PR1926](https://github.com/openpnp/openpnp/pull/1926)
* On launch, check if the window is off screen and, if it is, move it to the system default position. [PR1931](https://github.com/openpnp/openpnp/pull/1931)

# Version 2.4

## New Features

* Added the Pre-Rotate All Nozzles optimisation which provides a speed enhancement in situations where the rotation takes longer then the actual XY move eg when moving the second nozzle over the bottom camera and reduces the risk of parts slipping on nozzles. [PR 1654](https://github.com/openpnp/openpnp/pull/1654)
* A right-click menu to copy the machine position to the clipboard [PR 1727](https://github.com/openpnp/openpnp/pull/1727)
* Prevent unintended changes when a single click on a table row from opening cell editor or makes checkbox action. This now requires a second click. [PR 1729](https://github.com/openpnp/openpnp/pull/1729)
* Added the "Through-Board Depth" property to Parts. This can be used to record the height of any mechanical alignment pips, through-hole electrical pins, lenses on down-firing leds, and connectors with features that overhang the side of the board. This additional height is considered in Safe-Z calculations when moving such parts on a nozzle tip. [PR 1749](https://github.com/openpnp/openpnp/pull/1749)
* Added support for driver-side delaying using G4 P<> and uses it for static pick and place dwell time. This provides a better and tighter utilization of the machine while reducing the scheduler induced timing jitter. NB if your machine setup has highly tuned dwell times, it would be prudent to revisit that tuning after changing the machine delay implementation [PR 1699](https://github.com/openpnp/openpnp/pull/1699)
* Avoid unnecessary "Feeder X changed. Apply changes?" messages. [PR 1773](https://github.com/openpnp/openpnp/pull/1773)
* Many translation improvements. [PR 1658](https://github.com/openpnp/openpnp/pull/1658) [PR 1704](https://github.com/openpnp/openpnp/pull/1704) [PR 1803](https://github.com/openpnp/openpnp/pull/1803)
* Change camera view zoom behaviour from linear to log. [PR 1766](https://github.com/openpnp/openpnp/pull/1766)
* Added "Skip Next Feed" and "Disable Feed" feeder options. This provides limited recycle support to some feeders that previously had none. [PR 1716](https://github.com/openpnp/openpnp/pull/1716) [PR 1787](https://github.com/openpnp/openpnp/pull/1787)
* Speed up Photon feeder by avoiding unnecessary delays between feed and pick by changing polling strategy. [PR 1844](https://github.com/openpnp/openpnp/pull/1844)
* Changes relating to the job processor and placement optimisation:
  * Use axis accelation and feedrate parameters to estimate travel time when optimising pick and place locations, and travelling salesman routing. [PR 1813](https://github.com/openpnp/openpnp/pull/1813)
  * Additional placement sorting options making the order of placements predictable even for panels of identical boards. [PR 1658](https://github.com/openpnp/openpnp/pull/1658)
  * Added the order option _Nozzle Tips (Inflexible Tips First)_ which schedules the special-purpose nozzle tips first, the multi-purpose tips last, and then optimizes each of these groups using Pick and Place Locations. On a machine with multiple nozzles, this can help keep all the nozzles busy though to the end of the job. [PR 1799](https://github.com/openpnp/openpnp/pull/1799)
* Changes related to scripting:
  * A performance improvement for scripting events, for the common case where events do not have any scripts configured. Openpnp now remembers that the script does not exist and can skip a filesystem check on the next time that event is triggered. NB scripting users need to use the 'Clear Scripting Engine Pool' menu after adding a script, in the same manner as is needed when changing a script. [PR 1744](https://github.com/openpnp/openpnp/pull/1744)
  * The 'Job.Placement.BeforeAssembly' event now allows any scripts to fine-tune the location of the placement. This enables script-based 'local fiducial' behaviour. [PR 1688](https://github.com/openpnp/openpnp/pull/1688)
  * Added the `config.scriptState` object to hold state which is shared between scripts. This is stored in the `script-state.xml` file. [PR 1778](https://github.com/openpnp/openpnp/pull/1778)
  * Added actuator methods that do not rely on java method overloading, which are easier to call from scripting languages with different type systems. [PR 1806](https://github.com/openpnp/openpnp/pull/1806)
* Improvements to the part footprint camera overlay:
  * A marker to indicate the part orientation, typically pad #1, cathode, etc. [PR 1694](https://github.com/openpnp/openpnp/pull/1694)
  * Draw that overlay on bottom camera too. [PR 1745](https://github.com/openpnp/openpnp/pull/1745)
* Improvements to the manual jog interface:
  * Pressing the shift key reduces jog distance by 100x. [PR 1710](https://github.com/openpnp/openpnp/pull/1710)
  * Remember the position of the jog distance slider when OpenPnP is restarted. [PR 1690](https://github.com/openpnp/openpnp/pull/1690)
  * The jog buttons are disabled when a modal dialog box is shown. This prevents having multiple nested error message boxes when jogging into a soft limit. [PR 1761](https://github.com/openpnp/openpnp/pull/1761)
* Improvements for strip feeders:
  * A performance improvements relating to vision. It (optionally) no longer checks every single hole. [PR 1662](https://github.com/openpnp/openpnp/pull/1662)
  * A parallax vision feature for transparent tape where the holes can be difficult to see when viewed from above [PR 1713](https://github.com/openpnp/openpnp/pull/1713)
* Changes to fiducial vision. NB update your vision pipeline to include these new features:
  * The MatchTemplate vision pipeline stage now supports the 'center' and 'maxDistance' properties. The standard 'Footprint Fiducial' pipeline now support parallax vision features. [PR 1719](https://github.com/openpnp/openpnp/pull/1719)
  * All Fiducial pipelines now have a 'Max Distance' gui slider to control the detection range. This allows different fiducial pipelines to have different ranges, for example a panel fiducial might benefit from a larger detection range, and a board fiducial might benefit from a shorter detection range to avoid mis-detecting other board features. [PR 1719](https://github.com/openpnp/openpnp/pull/1719)

## Bug Fixes

* Fix bug causing a Feeder's Part configuration to change unexpectedly, when creating new parts. [PR 1775](https://github.com/openpnp/openpnp/pull/1775)
* Fix bug causing manual nozzle tip changes to get swallowed if the corresponding placement is set to defer errors. [PR 1741](https://github.com/openpnp/openpnp/pull/1741)
* Fix bug preventing the status bar "Placements N / M" from updating when viewing the wrong tab in the main window. [PR 1724](https://github.com/openpnp/openpnp/pull/1724)
* Fix ReferenceStripFeeder bug when calculating distance between reference sprocket holes. [PR 1714](https://github.com/openpnp/openpnp/pull/1714)
* Fix GUI memory leaks. [PR 1793](https://github.com/openpnp/openpnp/pull/1793)
* Fix for "Index -1 out of bounds for length 0" error message. [PR 1812](https://github.com/openpnp/openpnp/pull/1812)
* Marlin-specific fixes for I&S configuring GcodeAsyncDriver. [PR 1790](https://github.com/openpnp/openpnp/pull/1790)


# Version 2.2; 2024 Q4

## New Features

* Optimize fiducial checking in job using travelling salesman. [PR 1707](https://github.com/openpnp/openpnp/pull/1707)
* I&S supports controllers which send compressed position reports without whitespaces between the axes. [PR 1705](https://github.com/openpnp/openpnp/pull/1705)
* An option to skip auto-focus calibration of the up looking camera via issues & solutions. [PR 1700](https://github.com/openpnp/openpnp/pull/1700)
* Improved Chinese translation. [PR 1701](https://github.com/openpnp/openpnp/pull/1701) [PR 1703](https://github.com/openpnp/openpnp/pull/1703) [PR 1696](https://github.com/openpnp/openpnp/pull/1696)
* Tooltip delay timeout is prolonged to 1min. The text are rather long and require focus on info so long dismiss timeout. [PR 1691](https://github.com/openpnp/openpnp/pull/1691)
* On job error: Automatically select the nozzle, and update all linked tables for the cause. [PR 1678](https://github.com/openpnp/openpnp/pull/1678)
* Add Feeder.Before/AfterFeed scripts, and Feeder.Before/AfterTakeBack. These events can be used for stock control purposes. [PR 1685](https://github.com/openpnp/openpnp/pull/1685)
* Add Machine.AfterDriverHoming scripting event which gets called after homing all the drivers, and before calibration using the homing fiducial. [PR 1681](https://github.com/openpnp/openpnp/pull/1681)
* Pandaplacer Feeder - use full camera image for auto setup [PR 1686](https://github.com/openpnp/openpnp/pull/1686)
* A new I&S solution to warn if safe z is not in the conventional negative range [PR 1682](https://github.com/openpnp/openpnp/pull/1682)
* Improve usability of I&S solutions that suggest gcode changes [PR 1682](https://github.com/openpnp/openpnp/pull/1682)
* Nozzle tip loading strategy options in the job processor [PR 1659](https://github.com/openpnp/openpnp/pull/1659)

## Bug Fixes

* Fix the Nozzle background calibration diagnostics in case there is not a single pixel matching the worst-case limits [PR 1709](https://github.com/openpnp/openpnp/pull/1709)
* Main frame window divider does not work correctly when changing window size. Eg. when switching to multiwindow and back. [PR 1689](https://github.com/openpnp/openpnp/pull/1689)
* The Z offset of the second, third, etc. nozzle is now definitively calibrated by I&S solution [PR 1680](https://github.com/openpnp/openpnp/pull/1680)
* Fix bug in BlindsFeeder where the nozzle tip was moving in the wrong direction. [PR 1679](https://github.com/openpnp/openpnp/pull/1679)

## Installation and distribution changes

* Disabled bundling a JDK with the Win32 build. It's no longer available for download.
* Update install4j bundled JDK version from 17 to 23. 17 is no longer supported, and the minimum is 21, which is already considered out of date. 23 is the current supported version.
* Permanent fix for MacOS builds ([PR 1653](https://github.com/openpnp/openpnp/pull/1653))

## Internal Changes

* Design cleanup for ReferenceHeadMountable [PR 1687](https://github.com/openpnp/openpnp/pull/1687)



# 2024 Q3

## New Features

* Allow use of an arbitrary gstreamer pipeline as a video source. This can be for example a v4l2src, nvarguscamerasrc, rpicamsrc, rtsp receiver and decoder, media file reader and playbin, etc., etc [PR 1665](https://github.com/openpnp/openpnp/pull/1665)
* Avoid accidental leading or trailing whitespace in Part IDs etc [PR 1668](https://github.com/openpnp/openpnp/pull/1668)
* I&S works when using a generic G-code setup instead of using M115 (auto-discover known controller firmwares) [PR 1663](https://github.com/openpnp/openpnp/pull/1663)
* Avoid inefficient Z moves [PR 1656](https://github.com/openpnp/openpnp/pull/1656) [PR 1657](https://github.com/openpnp/openpnp/pull/1657)
* Allow some movement around the bottom camera to move without going via safe Z [PR 1657](https://github.com/openpnp/openpnp/pull/1657)

## Bug Fixes

* ReferencePushPullFeeder - use full camera image for auto setup [PR 1673](https://github.com/openpnp/openpnp/pull/1673)
* Fix nozzle calibration when using greyscale method [PR 1676](https://github.com/openpnp/openpnp/pull/1676)
* Default bottom vision pipeline size and position accuracy impovement [PR 1672](https://github.com/openpnp/openpnp/pull/1672)
* Various fixes for part size checking, and part size measurement using vision compositing. [PR 1671](https://github.com/openpnp/openpnp/pull/1671)
* Fix mirrored vision compositing preview. [PR 1670](https://github.com/openpnp/openpnp/pull/1670)
* Fix searchAngle parameter in minAreaRect vision pipeline stage [PR 1667](https://github.com/openpnp/openpnp/pull/1667)
* Fix ReferenceStripFeeder vision. [PR 1660](https://github.com/openpnp/openpnp/pull/1660)
* Fix exception handler that suppressed movement exceptions during vision [PR 1657](https://github.com/openpnp/openpnp/pull/1657)
* BambooFeeder removes the unwanted reset of a custom vision pipeline during Auto Setup [PR 1651](https://github.com/openpnp/openpnp/pull/1651)



# 2024 Q2

## New Features

* Improved masking for Multi-Shot vision pipeline. [PR 1638](https://github.com/openpnp/openpnp/pull/1638)
* BambooFeederAutoVision [PR 1622](https://github.com/openpnp/openpnp/pull/1622)
* If using a manual nozzle tip change: Jobs continue with just a single click after performing the requested nozzle tip change [PR 1617](https://github.com/openpnp/openpnp/pull/1617)
* Optimize placements of multi nozzle machines [PR 1574](https://github.com/openpnp/openpnp/pull/1574) [PR 1614](https://github.com/openpnp/openpnp/pull/1614)
* Changes the actuator usage for automatic nozzle tip changers to use False for unload and True for load. Previously it was using True for both load and unload. [PR 1620](https://github.com/openpnp/openpnp/pull/1620)

## Installation and distribution changes

* Modernize macOS app icon [PR 1633](https://github.com/openpnp/openpnp/pull/1633)
* Fix broken Camera permissions on MacOS Monterey [PR 1628](https://github.com/openpnp/openpnp/pull/1628)

## Internal Changes

* Update ReferencePushPullFeeder to use FeederVisionHelper [PR 1623](https://github.com/openpnp/openpnp/pull/1623)



# 2024 Q1

## New Features

* Add an option to automatically load the most recent job [PR 1616](https://github.com/openpnp/openpnp/pull/1616)
* If using a manual nozzle tip change: Combine the interruptions for unload and load. [PR 1609](https://github.com/openpnp/openpnp/pull/1609)
* Enhanced UI for Manual Change Location [PR 1611](https://github.com/openpnp/openpnp/pull/1611)
* Linux support for Neoden 4 cameras [PR 1604](https://github.com/openpnp/openpnp/pull/1604)
* Send FeedRate and Acceleration on change only [PR 1600](https://github.com/openpnp/openpnp/pull/1600)
* Allow children of panels to be replaced [PR 1598](https://github.com/openpnp/openpnp/pull/1598)

## Bug Fixes

* Fix pick location for ReferenceRotatedTrayFeeder [PR 1607](https://github.com/openpnp/openpnp/pull/1607) and ReferenceTrayFeeder [PR 1606](https://github.com/openpnp/openpnp/pull/1606)
* Fix manual change location shifted when recalibrating the camera to nozzle offset using I&S [PR 1612](https://github.com/openpnp/openpnp/pull/1612) [PR 1613](https://github.com/openpnp/openpnp/pull/1613)



# 2023 Q4

## New Features

* Log the time it takes to get a response from the write queue [PR 1595](https://github.com/openpnp/openpnp/pull/1595)

## Bug Fixes

* Fix erroneous reset of placement transform when cell selected not edited [PR 1597](https://github.com/openpnp/openpnp/pull/1597)



# 2023 Q3

## New Features

* Better Test Alignment. Take another picture after the part has been centered, making sure the lights are switched on and the camera image settled. Plus it also displays the final (overall) offsets result. [PR 1584](https://github.com/openpnp/openpnp/pull/1584)
* On a job error: select the object which was the cause of the error [PR 1577](https://github.com/openpnp/openpnp/pull/1577)
* Enhance the Job Placements table by immediately updating the Status column if a placement is enabled or disabled using the mouse [PR 1576](https://github.com/openpnp/openpnp/pull/1576)
* CSV UFT-16 support [PR 1573](https://github.com/openpnp/openpnp/pull/1573)

## Bug Fixes

* Proper condition for SimulationModeMachine simulated actuator delay [PR 1596](https://github.com/openpnp/openpnp/pull/1596)
* ReferenceRotatedTrayFeeder pick from correct location [PR 1451](https://github.com/openpnp/openpnp/pull/1451) [PR 1581](https://github.com/openpnp/openpnp/pull/1581)

## Installation and distribution changes

* It appears AdoptOpenJDK released a new version that only has builds for a few archs. Changed the version spec from "latest" the most recent with all the archs.
* Switch to the release jSerialComm 2.10.2 [PR 1571](https://github.com/openpnp/openpnp/pull/1571)

## Internal Changes

* AdvancedCameraCalibration aids in offline-debugging [PR 1583](https://github.com/openpnp/openpnp/pull/1583)
* ImageCamera fixes for using a picture of a real machine in simulation [PR 1579](https://github.com/openpnp/openpnp/pull/1579)



# 2023 Q2

## New Features

* Parallax fiducial locator [PR 1565](https://github.com/openpnp/openpnp/pull/1565)

## Bug Fixes

* Make sure the NashornScriptEngineFactory is always loaded [PR 1564](https://github.com/openpnp/openpnp/pull/1564)

## Installation and distribution changes

* Update Nashorn to version 15.4 for supporting Java 17 & 19 [PR 1563](https://github.com/openpnp/openpnp/pull/1563)
* Update openpnp-capture-java to 0.028 [PR 1562](https://github.com/openpnp/openpnp/pull/1562)



# 2023-05-03

Removed state from AbstractMachine. This might cause problems loading machine.xml in
the unlikely event that you configured a ActuatorSignaler with a non empty machine state.
To fix this, either remove the binding to machine state be setting it to empty before 
the upgrade or remove "MachineState" manually from the signalers section of your machine.xml.

Behaviour of ActuatorSignaler changed to only call the actuator if the job state has changed.

# 2023-05-02

Named CSV importer renamed to Reference CSV importer

Altium CSV importer added which accepts the default center-x/center-y columns and
correctly handles the rotation of bottom side parts.

# 2023-03-14

## Java 17+ Support

OpenPnP is now compatible with Java versions 11, 17, and 19. Thank you to @lags
and others! See the PR at [PR 1493](https://github.com/openpnp/openpnp/pull/1493) for more
details.

Other versions of Java are no longer explicity supported or tested but they may
still work. In general, any version 11+ should work.

The installers now include a current version of OpenJDK 17, rather than a very
out of date JDK 8.

## MacOS Silicon Support and Fixes

OpenPnP now supports Apple Silicon natively, including in openpnp-capture and
openpnp-opencv.

OpenPnP Capture Camera is now fixed on MacOS and should work correctly on both
x86 and Apple Silicon.

This version of OpenPnP changes from a installer to a single app archive.
You can install it by dragging the app to your /Applications folder.

The application and supporting files are now Code Signed so that they should
run without having to disable security.


# 2023-02-26

## Board Z

Changed the Capture Tool Location button on the Job table to only update the Z
and not the X, Y, or Rotation of the selected board. Also added the capability
to update multiple boards to the same Z value.

[PR 1527](https://github.com/openpnp/openpnp/pull/1527)

# 2023-02-14

## Panelization and other UI changes/improvements

Panels are now stand-alone entities much like boards. They are now stored in *.panel.xml files
rather than being "built-into" the job file. Panels can now have arbitrary layouts and can consist
of any number of different boards and/or subpanels. Many of the issues issues that have been
reported with the legacy panelization method have been fixed.

Two new tabs have been added to the UI. The Panels tab is the primary area for creating and editing
panels. The Boards tab is now the primary areas for creating and editing boards. The Job tab is now
primarily for selecting boards and/or panels (defined on the aforementioned tabs) to be assembled, 
setting their location and orientation on the machine and, of course, executing the job.

There is now a button on the Job tab (the Panels and Boards tabs have one as well) that opens a 
graphical viewer that displays the physical layout of the job (or Panel or Board).

The column widths on the Job, Panels, and Boards tabs are now remembered between OpenPnP sessions.
Numeric columns on those tabs are also now aligned on their decimal points. 

See also:
[PR 1507](https://github.com/openpnp/openpnp/pull/1507)

# Older Changes

Older changes to OpenPnp are recorded at https://github.com/openpnp/openpnp/blob/29d18e30346ac68c9b73221f6083ed4a7942fbf5/CHANGES.md
