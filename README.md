# kotlinjs-esbuild

Project to use faster esbuild instead of webpack.

## Available tasks:
* `browserDebugEsbuild`
* `browserDebugEsbuildRun`
* `browserReleaseEsbuild`
* `browserReleaseEsbuildRun`

Webpack:
* `build` folder using Esbuild: `1.079 Files, 300 Folders`: `165 MB` (most of the size because of dukat)
* `build` folder using Webpack: `7.146 Files, 1.143 Folders`: `210MB`  