# Third-party notices

## Everything Claude Code (ECC)

Selected workflow skill files in `.agents/skills/` are copied from or adapted from [affaan-m/ECC](https://github.com/affaan-m/ECC), version 2.2.3, under the MIT License.

Copyright (c) 2026 Affaan Mustafa.

The copied skill files retain their upstream attribution/license metadata. The MIT license text follows:

MIT License

Copyright (c) 2026 Affaan Mustafa

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Scope of this integration

This repository contains a selective project-local import of five ECC workflow skills. It does not include the complete ECC distribution, native plugin runtime, hooks, MCP server configuration, or all upstream agents and skills.


## Trader Skill Pack

BOBBY's Android-native Trader Skill Pack adapts the workflow concepts and skill descriptions from [Jakeschincariol/trader-skill](https://github.com/Jakeschincariol/trader-skill), Copyright (c) 2026 Jakes Chincariol, under the MIT License. This integration includes independently written Android UI and AI prompt workflows; it does not bundle or execute the upstream Python command-line tools.

MIT License

Copyright (c) 2026 Jakes Chincariol

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


## Optional offline Android voice engines

BOBBY's Offline Voice Library links to optional third-party Android TTS applications; their source code and model weights are not bundled into BOBBY. BOBBY uses Android's standard TextToSpeech API and follows the engine selected by the user in Android system settings.

- [NekoSpeak](https://github.com/DetentionHall/NekroSpeak) is an independently maintained offline TTS application. Its source repository declares the application code MIT-licensed; bundled models and dependencies may carry separate licenses. The repository's current Android minimum is API 26.
- [RHVoice](https://rhvoice.org/) is an independently maintained offline Android TTS engine. Install its app and voice data separately; consult its own license and voice-specific notices.
- [Marmalade TTS](https://github.com/maxwhipw/marmalade-tts-android) is an optional independent project, but its current Android release requires API 28 (Android 9), so it is not offered for installation from BOBBY on Android 8 (API 26).

These are external app links, not bundled libraries, and BOBBY does not claim to install or control them silently.
