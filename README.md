# Guitar-tuner

## 算法说明
- 输入：Oboe 采集音频（优先 AAudio），16bit 单声道。
- 预处理：Hann 窗；重叠窗口分析（默认 75% 重叠）。
- 音高检测：YIN（CMNDF）差分函数 + 抛物线插值估计周期，限制在 70–1300Hz。
- 稳定性处理：
  - 自适应噪声门限（噪声估计 + margin）。
  - 中值滤波 + 指数平滑，抑制抖动。
  - 弱信号下滞回，避免频率下跳。

## AAudio 说明
- 使用 Oboe 优先选择 AAudio；若实际后端为 AAudio，应用会用 Toast 提示。
