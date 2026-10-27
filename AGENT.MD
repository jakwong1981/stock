 Stock App - Vibe Coding 架構藍圖與開發規範指南 (`CLAUDE.md` / `.cursorrules`)

> **使用方式：** 將此檔案放置於你的 Stock App 專案根目錄（或在 Cursor / Windsurf / Claude Code / Qoder 中引用），為你的 AI 編碼代理 (AI Coding Agent) 提供明確的系統架構邊界、賽博朋克視覺風格、跨平台響應式導引、金融防呆規則與協作工作流程。

---

## 1. 專案概覽與目標 (Project Overview & Goals)
- **專案名稱：** `CyberStock Pulse`（具備賽博朋克美學之跨平台實時多市場股票投研與交易分析終端機）
- **核心價值主張：** 支援 Web 網頁版、iOS 與 Android 手機 App（透過 Capacitor / Flutter / React Native 跨平台架構），融合 **Cyberpunk 視覺美學（霓虹發光、深色透玻璃擬態 Glassmorphism、動態網格背景）**、**全市場毫秒級實時行情報道**、**熱門股票霓虹走馬燈 (Ticker Tape)**、**公司品牌 Logo 實時識別**、**即時財經新聞快訊串流**與 **AI 智能投研助手**。
- **支援平台（必須）：** 
  - **網頁版 (Web)：** 桌面瀏覽器（Chrome / Edge / Safari / Firefox 最新兩個主版本）與手機瀏覽器（iOS Safari、Android Chrome），並支援 PWA 安裝。
  - **手機 App：** iOS（iPhone / iPad）與 Android（手機 / 平板），透過 Capacitor 打包原生 App。
  - 所有平台共用同一套 Vue 3 程式碼庫與 Pinia Store，功能一致，僅在佈局與原生能力（震動、推播、安全儲存）上做平台差異化處理。
- **支援語言（必須）：** 繁體中文（`zh-HK` 香港 / `zh-TW` 台灣）、簡體中文（`zh-CN`）、英文（`en-US`）。預設依裝置 / 瀏覽器語言自動選擇，使用者可於設定中隨時切換並持久化保存。
- **當前階段：** 快速原型開發 (Rapid Prototype) -> 生產環境跨平台 MVP

---

## 2. 核心功能與賽博朋克視覺模組 (Core Functional & Cyberpunk Modules)
1. **跨平台全端響應式與 Cyberpunk 風格設計 (Multi-Platform Responsive & Cyberpunk UI/UX)：**
   - **全平台適配：** 佈局支援桌面端大螢幕 (Responsive Grid Terminal)、平板與手機 App（支援手機觸控手勢、底部導覽列、安全區域 Safe Area 適配、震動回饋）。
   - **Cyberpunk 視覺美學：** 採用高對比深色基底（`#0a0b10` 深夜黑 / `#050508` 虛擬空間黑），搭配霓虹色系發光邊緣（Neon Glow：霓虹青 `#00f0ff`、電光紫 `#bd00ff`、賽博黃 `#f3e600`）。使用玻璃擬態（Glassmorphism）與微發光陰影（Box-Shadow Glow）營造強烈的未來科幻金融終端機質感。
2. **股票實時行情報道與公司 Icon 識別 (Real-Time Ticker Stream & Brand Icons)：**
   - **實時行情報道：** 所有自選股、持倉與報價面板透過 WebSocket 進行毫秒級實時更新。價格跳動時帶有賽博霓虹閃爍特效（綠漲紅跌 / 紅漲綠跌可切換）。
   - **公司品牌 Logo 融合：** 所有股票報價條、清單、搜尋列與圖表標題旁，必須自動載入或對應顯示該上市公司的官方品牌 Icon / 股票 Logo（可透過高畫質 SVG CDN 或自建品牌 Logo 庫快取），提升視覺辨識度與專業感。
3. **熱門股票霓虹走馬燈 (Cyberpunk Ticker Tape Marquee)：**
   - 於頂部導覽列下方建置一條橫向無限流動的賽博霓虹走馬燈，自動輪播全市場熱門標的（如 `AAPL`, `NVDA`, `TSLA`, `0700.HK`, `9988.HK`）的即時價格、漲跌幅與公司 Logo。
   - 支援點擊走馬燈項目即時切換主視圖焦點。
4. **即時財經消息與新聞快訊串流 (Real-Time Financial News & Breaking News Stream)：**
   - 整合即時財經新聞 API（Benzinga, Bloomberg, Yahoo Finance），提供全天候快訊。
   - 結合 LLM 自動進行新聞情緒分析（利多/利空/中立色塊標記），並支援突發快訊霓虹彈窗提醒。
5. **互動式 K 線圖表與技術指標 (Interactive Charting & Technical Indicators)：**
   - 支援多時間週期（`1m` 至 `1M`）的專業 K 線圖（TradingView `lightweight-charts`），結合賽博霓虹風格的圖表色彩配置（Cyber-Green / Cyber-Red）。
6. **AI 金融投研與 MCP 智能代理 (AI Financial Research & MCP Agent)：**
   - 串接 LLM API 與模型上下文協定 (MCP)，讓 AI 助手能即時查詢持倉、歷史 K 線與基本面數據。
   - **LLM 供應商策略：所有 LLM 分析任務（新聞情緒、新聞摘要、投研對話、技術面解讀）一律優先使用 DeepSeek。**
     - DeepSeek API 相容 OpenAI 格式（`base_url = https://api.deepseek.com`），透過 OpenAI SDK 呼叫。
     - 模型名稱、base URL、逾時與重試次數全部由環境變數設定，不可寫死於程式碼。
     - 保留 Provider 抽象層（`LLMProvider` 介面），僅在 DeepSeek 不可用（逾時、限流、服務中斷）時才 fallback 至備用供應商；未設定備用金鑰時則回傳降級結果（如情緒標記為「未分析」）。

---

## 3. 技術棧與跨平台環境配置 (Tech Stack & Environment)
*(請根據當前專案實際採用的架構保留或取消註解對應選項)*

### 前端 (Web & Mobile App)
- **核心框架：** Vue 3 (Composition API, `<script setup lang="ts">`) + **Capacitor**（打包 iOS / Android 原生 App）或 Nuxt 3 / Vite。
- **狀態管理與路由：** Pinia（管理實時報價、走馬燈、新聞與投資組合狀態）、Vue Router。
- **UI 與賽博朋克樣式：** Tailwind CSS（自定義賽博朋克霓虹色盤、發光陰影與玻璃擬態樣式類別）、Lucide / Tabler 現代圖標庫。
- **金融圖表與走馬燈：** TradingView `lightweight-charts`、CSS3 Hardware-Accelerated Marquee (`transform: translate3d`)。
- **多語系：** `vue-i18n`（支援繁體 `zh-HK`, `zh-TW`、簡體 `zh-CN`、英文 `en-US`）。
  - 回退鏈：`zh-HK → zh-TW → en-US`、`zh-CN → en-US`；`zh-MO` 對應 `zh-HK`，`zh-SG` 對應 `zh-CN`。
  - 語言偵測：Web 使用 `navigator.languages`，App 使用 `@capacitor/device` 的 `getLanguageTag()`；使用者選擇存於 `localStorage` / `@capacitor/preferences` 並同步至使用者帳號。
  - 數字、貨幣、日期一律使用 `Intl.NumberFormat` / `Intl.DateTimeFormat` 依語系格式化。
  - 字型：`Noto Sans TC`（繁）、`Noto Sans SC`（簡）、等寬數字字型（報價用 `tabular-nums`）。
- **跨平台打包：** Capacitor（iOS / Android）+ PWA（`vite-plugin-pwa`，Web 可安裝）。

### 後端與系統架構 (Backend / Systems Architecture)
- **方案 A（核心業務 API 與投資組合服務）：** Java 21+ 搭配 Spring Boot 3.x、Spring Data JPA、Spring Security (JWT)、WebSocket。
- **方案 B（高頻實時行情與走馬燈廣播引擎）：** Rust（`Tokio`, `Axum`, `Tungstenite`, `Serde`, `SQLx` 高效能低延遲非同步多播）。
- **方案 C（量化分析、新聞情緒與 AI Agent 服務）：** Python 3.11+（`FastAPI`, `Pydantic`, `Pandas`, `NumPy`, `TA-Lib`）。

### 基礎設施、資料庫與 DevOps
- **資料庫：** PostgreSQL 搭配 **TimescaleDB**（時序 OHLCV 與 Tick 數據）。
- **快取與訊息佇列：** Redis（即時報價快取、走馬燈清單、WebSocket Pub/Sub）。
- **容器化與跨平台打包：** Docker 進行後端與 Web 端部署；Capacitor / Android Studio / Xcode 進行手機 App 原生打包。
- **機密管理（加密 `.env`）：** 使用 [`dotenvx`](https://dotenvx.com) 加密 `.env`。
  - `.env`：所有值以公鑰加密後儲存（`encrypted:...`），**可提交至 Git**。
  - `.env.keys`：存放解密私鑰（`DOTENV_PRIVATE_KEY`），**嚴禁提交**，必須列入 `.gitignore`；部署環境透過 CI Secret / 伺服器環境變數注入私鑰。
  - `.env.example`：僅列出變數名稱與說明，不含任何真實值。
  - 服務啟動方式：`dotenvx run -- ./mvnw spring-boot:run`、`dotenvx run -- uvicorn app.main:app`，於程序啟動時在記憶體中解密，不落地明文。
  - 新增或修改金鑰：`dotenvx set DEEPSEEK_API_KEY <value>`（自動加密）。

---

## 4. 金融領域核心防呆守則 (Financial Domain Guardrails)

1. **嚴禁使用浮點數進行金額與股數運算：**
   - 全程使用高精度十進位型別（Java `BigDecimal`、Rust `rust_decimal::Decimal`、Python `decimal.Decimal`、前端 `decimal.js`）。資料庫欄位統一採用 `NUMERIC(18, 6)` 或 `NUMERIC(20, 8)`。
2. **時區與交易時段嚴格管控：**
   - 資料庫統一儲存 UTC (`TIMESTAMPTZ`)，並透過交易所時區（`America/New_York`, `Asia/Hong_Kong`）精確計算開收盤與盤前盤後時段。
3. **跨平台手機 App 效能與觸控優化：**
   - 手機 App 端必須優化 DOM 節點與 WebSocket 重連機制。高頻報價更新時必須進行節流（Throttling，每 500ms 批次更新），並確保在行動網路切換（Wi-Fi 轉 5G）時能自動無縫重建 WebSocket 連線。
4. **賽博朋克視覺可讀性保證：**
   - 霓虹發光與深色玻璃擬態雖具備強烈未來感，但必須確保在手機戶外強光下或低亮度時，數字、走馬燈文字與圖表走勢依然具備極高的對比度與辨識度。
5. **多語系強制規範：**
   - 嚴禁在元件中硬編碼任何使用者可見文字，一律使用 `t('key')`。新增 key 時必須同時補齊 `zh-HK`、`zh-TW`、`zh-CN`、`en-US` 四份語系檔（CI 檢查缺漏 key）。
   - 金融術語須依地區用語翻譯（例如：港「孖展」/ 台「融資」/ 陸「融资」；港「窩輪」/ 台「權證」/ 陸「权证」），不可單純繁簡轉換。
   - 後端 API 錯誤訊息回傳錯誤代碼 (error code)，由前端依語系翻譯；AI 新聞摘要與投研回覆依使用者語系生成。
   - UI 佈局須容納英文較長字串與中文較短字串的寬度差異，避免截斷或溢出。
6. **API 金鑰與機密安全：**
   - LLM、行情、新聞等所有第三方 API 金鑰**只能存在於後端**（`analytics-ai/`、`backend/`），嚴禁打包進前端 Web 或手機 App（Vite 的 `VITE_*` 變數會被公開，禁止用於任何金鑰）。
   - 嚴禁在程式碼、日誌、錯誤訊息、測試快照中輸出金鑰；日誌需對 `Authorization`、`api_key` 等欄位做遮罩。
   - CI 加入 secret scanning（如 `gitleaks`），並以 pre-commit hook 檢查 `.env` 是否皆為加密值、`.env.keys` 未被加入版本控制。
   - 送往 DeepSeek 的內容需最小化：投研對話只傳必要的持倉摘要，不傳送使用者 email、姓名等個人識別資訊 (PII)。
7. **全平台一致性：**
   - 每項功能需於 Web 桌面、Web 手機、iOS、Android 四種環境驗證。原生能力需提供 Web 降級方案（如 Haptics 於 Web 端靜默略過、推播於 Web 端改用 Web Push）。

---

## 5. AI Agent 協作與工作流程守則 (Agentic Workflow Rules)

1. **先規劃後寫碼 (Plan Before Coding)：**
   - 涉及跨平台導覽、手機觸控手勢、WebSocket 實時走馬燈、公司 Icon 載入或損益計算公式前，必須先列出影響的檔案清單。
2. **漸進式與原子化修改 (Incremental & Atomic Changes)：**
   - 確保 Web 端與手機 App 程式碼共用性（如透過 Capacitor 載入 Vue 3 單頁應用或共享 Pinia Store），避免產生兩套完全割裂的程式碼庫。
3. **錯誤處理與自我驗證：**
   - 撰寫完成後主動執行 `npm run build`、`cargo check`、`pytest` 等指令，確保跨平台建置無誤。

---

## 6. 核心資料表結構參考 (Core Data Models Reference)

```sql
-- 1. 金融商品主檔（含公司 Logo 與走馬燈標記）
CREATE TABLE instruments (
    symbol VARCHAR(20) PRIMARY KEY,       -- 例如：'AAPL', '0700.HK'
    name VARCHAR(120) NOT NULL,
    exchange VARCHAR(20) NOT NULL,        -- 'NASDAQ', 'NYSE', 'HKEX'
    currency VARCHAR(10) NOT NULL,        -- 'USD', 'HKD', 'TWD'
    timezone VARCHAR(50) NOT NULL,        -- 'America/New_York', 'Asia/Hong_Kong'
    logo_url VARCHAR(500),                -- 公司官方 Logo / SVG 圖示連結
    is_active BOOLEAN DEFAULT TRUE,
    is_marquee BOOLEAN DEFAULT FALSE      -- 是否納入賽博霓虹走馬燈廣播
);

-- 1b. 金融商品多語系名稱（繁 / 簡 / 英）
CREATE TABLE instrument_names (
    symbol VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    locale VARCHAR(10) NOT NULL,          -- 'zh-HK', 'zh-TW', 'zh-CN', 'en-US'
    name VARCHAR(120) NOT NULL,           -- 例如：'騰訊控股' / '腾讯控股' / 'Tencent Holdings'
    short_name VARCHAR(60),
    PRIMARY KEY (symbol, locale)
);

-- 2. 即時財經新聞快訊資料表
CREATE TABLE financial_news (
    id UUID PRIMARY KEY,
    symbol VARCHAR(20) REFERENCES instruments(symbol),
    title VARCHAR(255) NOT NULL,
    summary TEXT,
    source VARCHAR(100) NOT NULL,
    url VARCHAR(500) NOT NULL,
    sentiment_score NUMERIC(3, 2),        -- AI 情緒分數 (-1.00 至 1.00)
    published_at TIMESTAMPTZ NOT NULL
);

-- 3. 歷史 OHLCV K線資料表 (TimescaleDB)
CREATE TABLE ohlcv_candles (
    symbol VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    interval VARCHAR(10) NOT NULL,        -- '1m', '5m', '1d'
    bucket_time TIMESTAMPTZ NOT NULL,
    open NUMERIC(18, 6) NOT NULL,
    high NUMERIC(18, 6) NOT NULL,
    low NUMERIC(18, 6) NOT NULL,
    close NUMERIC(18, 6) NOT NULL,
    volume NUMERIC(20, 2) NOT NULL,
    PRIMARY KEY (symbol, interval, bucket_time)
);

-- 4. 投資組合交易明細帳本
CREATE TABLE portfolio_transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    symbol VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    side VARCHAR(10) NOT NULL,            -- 'BUY', 'SELL', 'DIVIDEND'
    quantity NUMERIC(18, 6) NOT NULL,
    price NUMERIC(18, 6) NOT NULL,
    fee NUMERIC(18, 6) DEFAULT 0,
    currency VARCHAR(10) NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL
);
```

---

## 7. 專案目錄結構參考 (Directory Structure Reference)

```text
.
├── docker-compose.yml               # PostgreSQL (TimescaleDB), Redis, 後端與實時 WebSocket 服務
├── .env.example                     # 環境變數範本（僅變數名稱，無真實值）
├── .env                             # dotenvx 加密後的環境變數（可提交）
├── .env.keys                        # dotenvx 解密私鑰（.gitignore，嚴禁提交）
├── README.md
├── capacitor.config.ts              # 跨平台手機 App (iOS/Android) 打包配置
├── ios/                             # Capacitor 產生的 iOS 原生專案 (Xcode)
├── android/                         # Capacitor 產生的 Android 原生專案 (Android Studio)
├── frontend/                        # Vue 3 + Tailwind (Cyberpunk Theme) + Lightweight Charts
│   ├── src/
│   │   ├── api/                     # REST API 與 DTO 定義
│   │   ├── components/
│   │   │   ├── cyberpunk/           # CyberNeonCard.vue, GlassPanel.vue, GlowButton.vue
│   │   │   ├── market/              # TickerTapeMarquee.vue (霓虹走馬燈), TickerRowWithIcon.vue (帶 Logo 報價列)
│   │   │   ├── charts/              # CandlestickChart.vue
│   │   │   └── news/                # BreakingNewsTicker.vue
│   │   ├── composables/             # useMarketStream.ts, useNewsStream.ts, useCapacitorApp.ts
│   │   ├── locales/                 # zh-HK.json, zh-TW.json, zh-CN.json, en-US.json
│   │   ├── stores/                  # useQuoteStore.ts, useMarqueeStore.ts, useNewsStore.ts
│   │   └── views/                   # DashboardView.vue, StockDetailView.vue, PortfolioView.vue
├── backend/                         # Spring Boot / Rust 核心 API 與實時 WebSocket 伺服器
└── analytics-ai/                    # Python FastAPI + AI 新聞情緒 + MCP 智能代理

