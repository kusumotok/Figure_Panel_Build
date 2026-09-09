# Poster / Scientific Layout Editor Plan (rev.2)

## 0. Goal

既存の `Figure_Panel_Build` を起点に、Scientific Figure を構成要素として再利用しながら、Panel / Poster / Slide / Document まで拡張可能な再帰的レイアウトエディタへ発展させる。

基本思想は以下。

- **Figure → Panel → Poster という固定階層を機能上は作らない**
- すべてを同一の **Node** として扱い、Node を再帰的に入れ子にする
- 「Poster」「Panel」「Figure frame」などは意味上の呼び方にすぎず、背景・枠・角丸・padding・layout 等の能力は全階層で共通
- 固有機能は Node の **Content** 側に持たせる
- UI は簡潔に保ち、内部モデルだけ高度にする
- Scientific image 編集は既存 `FigureConfiguration` の思想を最大限再利用する
- PPTX は通常のPowerPoint互換出力であると同時に、**project container** として再open可能にする

## 0.1 確定事項

| 項目 | 決定 |
|---|---|
| 既存リポジトリとの関係 | 既存コードを取り込み共存。`org.microscopy.figure` は無改変で残し、Poster エディタは新パッケージ `org.microscopy.panel` + 別 SciJava コマンド |
| リリース範囲 | 初回から完成品。§20 の S1〜S11 は出荷単位ではなく内部の実装順序 |
| プロジェクト正本 | **PPTX**。ただし Save は「path 参照 + プレビュー品質サムネイル焼き付け + project part」、フル解像度は Export に分離（§15） |
| Size 制約 | 閉じた関係集合に限定。任意数式は後フェーズへ延期（§5） |
| PowerPoint 側編集の取り込み | text / rich text / font / size / color / fill / border のみ。幾何・回転等は報告のみ（§16） |

---

# 1. Existing Repository: Current State

現状の `Figure_Panel_Build` は主に pixel-based。

## 1.1 FigureConfiguration

- 各 cell のサイズは明示的な物理サイズではなく、入力画像の `width × height` をそのまま使用
- 異なる画像サイズは validation で拒否
- `horizontalGap` / `verticalGap` も px 単位
- Figure 全体の物理寸法（mm / inch）という概念はない

## 1.2 InputImageManager.Source

保持しているもの: width / height [px]、channels、bitDepth、pixelWidth / pixelHeight、calibration unit、path、pixel snapshot。

現状は Z/T > 1 を拒否し、C のみを扱う。**全 plane を `duplicate()` で eager 保持**している。

## 1.3 FreeBuildConfiguration

- `cellWidth`, `cellHeight`, `gap`, `fontSize` — すべて実質 px
- 全 cell 同サイズ、cell span / merge なし、1 slot = 1 image
- 未完成。Poster エディタが上位互換になるため置き換え対象

## 1.4 PPTX Export

- ノード単位（セル単位）の picture + ベクタのテキスト/図形として書いており、**全体を 1 枚のラスタにしない構造は既にできている**
- ただし canvas 長辺を固定 EMU 長（12192000）へ正規化しており、「何 mm の Figure か」という設計ではない

## 1.5 再利用判断

| 既存資産 | 判断 |
|---|---|
| `ImageRenderer`（B&C・LUT・additive merge・crop+scale） | 再利用。ScientificImageContent の描画本体。§6.3 の高速化を適用 |
| `FigureConfiguration` / `ChannelConfig` / `DisplayChannel` / `ConditionConfig` | ScientificImageContent のペイロードとしてそのまま格納。新経路では「全条件で同一サイズ」検証を緩める |
| `ScaleBarRenderer` / `LabelRenderer` / `InsetRenderer` / `InsetConfig` / `InsetCell` | 再利用。px 前提なので「Content ローカル px 空間」に閉じ込める |
| `AppearanceDefaults`（長辺 % で初期値算出） | 思想を継承し mm 版に写す |
| `FigureHistory`（Gson 全体スナップショット + coalescing + 100 件上限） | 設計を継承。Document JSON は画像を含まないので軽い |
| `PptxExporter` | 構造を再利用。EMU 換算と customXml part を差し替え・追加 |
| `SettingsSerializer` / `FreeBuildSettings`（相対パス化・temp→move の原子的保存・version 判別） | パターンを再利用 |
| `OutputSafety`（元 TIFF 上書き禁止） | 再利用。新経路でも必ず通す |
| `InputImageManager.Source` | 要改修（§12） |
| `FreeBuildConfiguration` / `FreeBuildDialog` | 置き換え対象。新経路が動いた時点で deprecated 表示 |

---

# 2. Core Architecture

## 2.1 Document / Page / Node

```text
Document
├─ schemaVersion
├─ tokens{}          … 名前付き値
├─ styles{}          … Token 参照の集合
├─ assets{}          … Asset DB
└─ pages[]
   └─ Page { size, margins, rootNode }
      └─ Node
         ├─ id (stable UUID)
         ├─ layout    { mode: Grid|Flow|Stack, tracks, gaps, align }
         ├─ size      { width: SizeExpr, height: SizeExpr, min, max, aspect }
         ├─ placement { row, col, rowSpan, colSpan, zIndex }
         ├─ appearance{ styleRef?, overrides{} }
         ├─ children[]
         └─ content
```

Node の能力は階層によらず共通。**Root Node 1 本ではなく Pages[] を持つ**（ポスター 1 枚でも、スライド / 複数図版 / 表裏で必ず要る。後から入れると ID 体系と PPTX slide 対応を作り直すことになる）。

## 2.2 Content

Content のみが固有機能を持つ。

```text
Content
├─ None / Container
├─ TextContent
├─ ScientificImageContent
├─ ImageContent
└─ ShapeContent
```

### ScientificImageContent

既存 Figure system を継承。

```text
{ assetId, c/z/t selector, figureConfig }
```

`figureConfig` は既存 `FigureConfiguration` をそのまま格納する。これにより C/Z/T selection・LUT・B&C・Merge・Scale bar・Inset・Crop が既存実装のまま使える。

### TextContent

```text
{ paragraphs[], flowRegion?, overflowPolicy }
```

Paragraph・Rich text runs・Flow region・inline の bold / italic / underline / superscript / subscript / color / font / size override。

## 2.3 単一の真実：LayoutResult

レイアウト計算結果は `LayoutResult`（nodeId → mm 矩形の不変マップ）に集約し、**canvas 描画・hit test・PPTX・PDF・raster 出力がすべてこれを読む**。

現状コードではセル座標の計算式（`x0 = rowRight ? 0 : rowBand`）が `PanelLayoutEngine` / `FigureWorkspace` / `PptxExporter` の 3 箇所に重複している。Node 再帰でこれを再発させない。

## 2.4 主要な新規クラス（`org.microscopy.panel`）

| クラス | 責務 |
|---|---|
| `Document` / `Page` / `Node` / `Content` 系 | モデル（Gson で素直に往復する public field スタイル、既存流儀に合わせる） |
| `SizeExpr` | 閉じた関係集合 |
| `LayoutEngine` → `LayoutResult` | 2 パス measure / arrange |
| `RenderTarget` / `NodeRenderer` | dpi と描画（Content ごとに委譲、画像は既存 `ImageRenderer`） |
| `TileRasterizer` | PNG / TIFF のタイル逐次出力 |
| `TokenTable` / `StyleTable` / `StyleResolver` | Token → Style → Node の解決 |
| `RichText` / `FlowRegion` / `TextFlowEngine` | AttributedString / LineBreakMeasurer / Area |
| `AssetLibrary` / `AssetSource`（`TiffSource`, `BioFormatsSource`, `OpenImageSource`） | 遅延 plane 供給・fingerprint・relink |
| `ProjectPart` / `PptxProjectWriter` / `PptxProjectReader` / `PptxDiff` | PPTX 正本の読み書きと差分 |
| `LegacyImporter` | v1 settings JSON → Document |
| `PosterFrame` / `PosterCanvas` / `Inspector` / `StyleManagerPanel` | UI |

---

# 3. Physical Units and Flexible Document Size

## 3.1 Internal units

```text
Document / Page / Node geometry ─ mm (double)
Content 内部（画像・inset・scale bar）─ px（既存コードの世界を温存）
PPTX ─ EMU (long)   1 mm = 36000 EMU（厳密）
文字 ─ pt           1 pt = 25.4/72 mm
ラスタ出力 ─ px     px = mm / 25.4 × dpi
```

- 論理値は mm の double。出力時に必ず整数 EMU へ丸める。丸めは累積誤差を避けるため「絶対座標を丸めてから幅を差分で求める」方式。
- 旧 `factor = 12192000 / max(dim)` による長辺正規化は廃止する。**A0 は A0 として出す。**
- Content 内部は px のまま。Node が与えた mm 矩形と Content の intrinsic px サイズの比が、その画像の **実効 dpi**。
- `UnitFormat` で mm / inch / pt の表示切替（内部は常に mm）。

## 3.2 Document size

初手で A0 / A1 等を強制選択しない。

```text
Canvas Size: Auto
```

Auto の間は content に応じて canvas が伸びる。後から切り替え可能。

```text
Auto
A0 Portrait / Landscape
A1 / A2
Journal: 単段 85 mm / 1.5 段 114 mm / 全段 180 mm（編集可能なプリセット表）
Custom (W × H)
幅のみ固定
```

Journal column preset を持つのが実務上は最重要（実際の投稿要件はこれ。A0 より使用頻度が高い）。

Preset 適用時は `Fit（全体を縮小）/ Scale（レイアウトごと拡大縮小）/ Reflow（トラックを再配分）` を選択。既定は Reflow、プレビュー付き。

**PowerPoint のスライド上限 1422.4 mm (56 in) を超える設定は警告**する（A0 = 841 × 1189 mm は収まる）。

---

# 4. Layout System

## 4.1 Layout modes

Node は layout mode を持つ。

```text
Grid
Flow
Stack
```

### Grid

基本思想。rows / columns、unequal track sizes、span、nested grid、gaps、alignment。

トラックサイズは SizeExpr。**canvas 上の divider drag が `Fraction` の重み変更**にマップされ、数式を入力せずに比率を調整できる。

### Flow

Text や sequential content 用。

### Stack

advanced 用。同一領域への overlay、local z-order、annotation / inset / label / arrow 等。

通常UIでは Grid を中心にし、Stack は右クリック「重ね合わせに変換」からのみ到達する。

---

# 5. Size / Constraint Model

## 5.1 SizeExpr（閉じた集合）

任意数式のエンジンは作らない。以下の閉じた集合に限定する。

| 種別 | 意味 |
|---|---|
| `Auto` | Content の intrinsic か children から決まる |
| `Fixed(mm)` | 絶対値 |
| `Fraction(n)` | 親の残余を兄弟間で n:m 配分（CSS の `fr`） |
| `Percentage(p)` | 親の内側寸法の p% |
| `SameAs(siblingId)` | **同一親内の兄弟のみ**を参照 |
| `AspectRatio(r)` | もう一方の辺 × r。Content の intrinsic 比を初期値に |
| `Min(mm)` / `Max(mm)` | 上記の結果をクランプ |

`AspectRatio` は科学図版で最頻出の制約（画像を歪ませない）。`Min` / `Max` と併せて第一級で持つ。

「幅と高さの両方を同時に AspectRatio にはできない」等の妥当性は入力時に検査する。

## 5.2 解決順序（汎用 dependency graph を使わない）

1. `Fixed` / `Percentage` を確定
2. `Auto` を measure パスで確定（Content intrinsic → children 再帰）
3. `Fraction` を残余から配分
4. `SameAs` / `AspectRatio` を伝播（同一親内・トポロジカル順。参照が兄弟に閉じるので循環は構造的に起きない）
5. `Min` / `Max` でクランプ → 変化があれば同一親内で 3〜5 を再実行（最大 3 回。収束しなければ最後の値を採用して警告）

## 5.3 User-facing UI

通常UI:

```text
Width   [ Fill ▾ ]
Height  [ Aspect 4:3 ▾ ]
```

Options: Auto / Fill / Fixed / Percentage / Same as… / Aspect / Advanced…

Advanced を開いた時だけ min / max / weight を出す。

```text
Advanced…  Min 80 mm   Max 160 mm   Weight 2fr
```

`Same as…` の候補は同一親内の兄弟のみを列挙する（循環候補を無効化する UI が不要になる）。

## 5.4 Formula（後フェーズ）

`= parent.width * 0.35` のような任意式は初回リリースでは露出しない。閉じた関係集合で実用の大半を満たせるため、表計算エンジン相当の実装コストを初期から外す。

---

# 6. Rendering and Resolution

mm 論理座標と px 実画像を橋渡しする「実効 dpi」「出力 dpi」は、印刷機能ではなく **コアの一級概念**として最初から持つ。

## 6.1 原則：ポスター全体を一度もラスタライズしない

| 出力 | 方式 |
|---|---|
| PPTX | ノード単位の picture + テキスト / 図形はベクタ shape |
| PDF | 同様にノード単位。テキストはベクタ + フォント埋め込み |
| PNG / TIFF | `TileRasterizer` が LayoutResult を走査し、タイル（例 2048×2048）ごとに関係ノードだけ描いて逐次書き出し |
| プレビュー | 表示スケールに応じた LOD。タイルキャッシュを `(nodeId, contentHash, targetPx)` で持つ |

- A0 @ 300 dpi は 9933 × 14043 = 約 139 MP。ポスター全体を 1 枚の `BufferedImage` にする設計は成立しない。
- 既存 `PanelLayoutEngine.dimensions` の **100 MP 上限は Content ローカル（1 画像 = 1 Figure）にのみ残し、Document level では撤廃**する。
- PNG のタイル逐次出力は Deflater でスキャンラインを流す最小実装（`ImageIO` は部分書きに向かない）。TIFF は ImageJ の `TiffEncoder` / `FileSaver` 経路を使う。

## 6.2 実効 dpi と警告

- 各 ScientificImage について `effectiveDpi = sourcePx / (mm / 25.4)`。
- 出力ダイアログに一覧表示し、`< 300 dpi` を黄警告、`< 150 dpi` を赤警告。
- `Fit to native pixels` = その画像の mm サイズを `sourcePx / targetDpi × 25.4` に合わせる（等倍・再サンプルなし）。
- リサンプル方式は Content ごとに `Nearest`（既定・定量性重視）/ `AreaAverage`（縮小時の見た目重視）を選択可。**勝手に補間されないことが要件になりうる**ため既定は Nearest。

## 6.3 ImageRenderer の改修（後方互換を保つ）

- 既存シグネチャを維持したまま内部を差し替え：`BufferedImage` の `DataBufferInt` へ直書き（現状は per-pixel `setRGB`）。
- `ChannelConfig` ごとに `min / max / lut / invert` から RGB テーブルを事前計算（8-bit は 256 段、16-bit は量子化して 4096 段）。
- 縮小時に `AreaAverage` を選べるオーバーロードを追加。
- `range()` の全画素走査は結果を `(assetId, channel)` でキャッシュ。

---

# 7. Text Flow Region

Text area は必ずしも矩形に限定しない。

## 7.1 実装方針

`AttributedString` + `java.awt.font.LineBreakMeasurer` + `java.awt.geom.Area` で実装する。

- 非矩形は「行バンド ∩ Area」で得た区間列に対して行を流す
- rich text run は `TextAttribute`（WEIGHT / POSTURE / UNDERLINE / SUPERSCRIPT / SIZE / FAMILY / FOREGROUND）で表現
- CJK の改行も標準実装に乗る

## 7.2 Region as geometry

```text
FlowRegion = union(cells) - exclusions
```

例:

```text
2 x 2

┌──────┬──────┐
│ text │Figure│
├──────┼──────┤
│ text │ text │
└──────┴──────┘
```

Text region:

```text
cell(0,0) ∪ cell(1,0) ∪ cell(1,1)
```

L字型でもそのまま flow する。

Flow region は「セルを複数選択して**テキスト領域に結合**」した時にだけ現れる概念とし、通常は矩形テキストボックスだけを見せる。

## 7.3 Disconnected islands

離れた島がある場合のみ option を出す。

```text
Flow order:
Automatic / Left → Right / Right → Left / Top → Bottom / Manual
```

Manual の場合は canvas overlay で ① ② ③ の順番を編集。

## 7.4 Overflow

Default は **Warn**。Options: Warn / Auto shrink / Expand region / Clip。

Poster 全体の typography consistency を壊さないため、勝手な auto shrink は default にしない。ノードに ⚠ バッジ、status bar に件数、クリックで選択ポップオーバー（適用前プレビュー付き）。

## 7.5 PPTX との非互換（重要）

**PowerPoint のテキストフレームは矩形のみで、L 字領域を「編集可能なまま」PPTX に書く方法は存在しない。** これを仕様として明示する。

- Export ダイアログで書き出し方を選ばせる
  - `矩形に自動分割`（既定・編集可能なまま・見た目は概ね維持）
  - `行単位テキストボックス`（見た目厳密・編集性は低下）
  - `画像として書き出す`（見た目厳密・編集不可）
- **正本 PPTX（Save）では常に `矩形に自動分割` 固定。** 領域そのものは project part に無損失で入るので情報は失われない。分割 shape は `nodeId#part1..N` と命名し、text 取り込み対象から外す（§16）

---

# 8. Style System

## 8.1 Token → Style → Node の 2 段

```text
Token   { id, name, kind(Color|Length|FontSize|FontFamily|…), value }
Style   { id, name, properties{ key → Literal | TokenRef | Inherit } }
Node    { styleRef?, overrides{ key → Literal | TokenRef } }
```

解決順：Node override → Node style → 親 Node から継承 → 既定値。

Text style / Panel style / Figure style と内部的に分けない。すべて同じ Style system を使用する。

## 8.2 Style から Style は参照しない

共有は Token 経由にする。これで「本文と Figure label のサイズは共通、font は別、Japanese font は共通」がそのまま表現でき、かつ DAG が構造的に保証されるため循環検出・ピッカー無効化・再評価という重い機構が不要になる。

```text
Token  "body-size" = 28 pt
Token  "cjk"       = Yu Gothic

Style  Body        { fontSize → body-size, latin = Times New Roman, ea → cjk }
Style  FigureLabel { fontSize → body-size, latin = Arial,           ea → cjk }
```

Token が Token を参照するのは別名 1 段のみ許可し、その 1 段だけ検査する。

## 8.3 Style Manager UI

コンパクトな一覧。

```text
Styles
Body
Panel Heading
Figure Label
Card
Emphasis
…
```

Drag & drop で Node に適用可能。drop 先は hover 中の Node をハイライトし、ネストは修飾キー（Alt で親へ）で切り替える。

## 8.4 Property Inspector

Property ごとに Token 参照と direct value を同じUIで扱う。

```text
Font Size
[ ↗ body-size · 28 pt ▾ ]
```

または

```text
[ 28 pt ]
```

---

# 9. Typography

Latin / CJK font は別指定。

```text
Latin font
Japanese / CJK font
```

Style に `latinFamily` / `eaFamily` / fallback リストを持たせ、PPTX の `<a:latin>` / `<a:ea>` に直結する。既存 exporter のハードコード（`Arial` / `Yu Gothic`）を Style binding に置き換える。

**未インストールフォントは代替解決して警告**する。

## 9.1 Rich text

同一 TextContent 内でも一部だけ bold / italic / underline / font / size / color / superscript / subscript を run override できる。Base style は維持。

---

# 10. Appearance / Frame Styling

背景・角・border 等は Panel 専用機能にしない。どの Node にも適用可能。

```text
Appearance
├─ fill
├─ border color
├─ border width
├─ border style
├─ corner radius
├─ shadow
├─ opacity
├─ padding
├─ clipping
└─ visibility
```

Poster root に background、Section に角丸、Figure group に border、Text area に background、Caption box に fill — すべて同じ仕組み。

---

# 11. Layer / Z-order

過剰に複雑な global layer system は作らない。

## 11.1 Local z-order

各 parent の direct children だけ z-order を持つ。UI は Bring forward / Send backward / child list reorder。

## 11.2 Stack container

重ね合わせが必要な場合のみ Stack container を使用する。

```text
Stack
├─ Image
├─ Annotation
├─ Label
└─ Arrow
```

通常ワークフローでは前面に出しすぎない。

---

# 12. Asset Model

Node が直接 file path を持たず、Asset DB を中央管理する。

```text
Asset
├─ id
├─ originalUri / embeddedUri
├─ originalFormat / originalSeriesIndex
├─ sizeX / Y / C / Z / T
├─ calibration (pixelWidth / pixelHeight / unit)
├─ bitDepth
├─ metadata
├─ fingerprint (size + mtime + 先頭ハッシュ)
└─ storageMode (Linked | Portable)
```

ScientificImageContent は `assetId` と `C / Z / T selector` のみを持つ。

## 12.1 遅延 plane 供給

`AssetSource` インタフェース = `ImageProcessor plane(c, z, t)`。

| 実装 | 方針 |
|---|---|
| `TiffSource` | 遅延読み + LRU（既定 512 MB 目安、設定可） |
| `BioFormatsSource` | `provided` の Bio-Formats を reflection 経由で使い、未検出時は TIFF のみに degrade |
| `OpenImageSource` | **Fiji で開いている画像は従来通り即時スナップショット**（ユーザーが編集しうるため遅延不可。既存 `InputImageManager.snapshot` の不変条件を維持） |

現状の「全 plane を eager に `duplicate()`」は Z/T/多 series でメモリが破綻するため、ファイル由来のソースは遅延に変える。

## 12.2 Relink

元ファイルが移動した場合:

- `Locate file`
- `Search folder`（fingerprint で候補照合）
- `Use embedded preview`

**`Use embedded preview` の実体は、正本 PPTX に焼き付けられたプレビューサムネイル**（§15.1）。この場合はノードに「プレビュー品質」バッジを出し、Export 時に警告する。

---

# 13. Source Formats and Bio-Formats

## 13.1 Linked Project

元ファイルをそのまま参照する。対応候補: TIFF / OME-TIFF / LIF / OIB / OIF / ND2 / OIR / その他 Bio-Formats compatible。

Project metadata には source URI / series index / C・Z・T selection / file size / modified time / fingerprint を保存する。

**Bio-Formats は `provided` スコープ**とし、shade しない（Fiji 同梱、GPL）。Gson のみを shade する現行方針を壊さない。

## 13.2 Portable Project

Bio-Formats container 全体は埋め込まない。

### 原則

**1 source = 1 series = 1 XYCZT dataset**

Portable 化時:

- 選択 series を独立した TIFF / OME-TIFF に materialize
- C/Z/T は保持
- original file / original series 情報は provenance として残す

```text
experiment.lif / Series 2
→ embedded/source-uuid.ome.tif   (XYCZT retained)
```

複数 series を巨大な1つの OME-TIFF にまとめない。

---

# 14. Save / Export / Print

保存と出力を明確に分離する。**この 2 つの混同が唯一の footgun** なので、名前・ショートカット・ダイアログで徹底的に分ける。

## 14.1 Save（プロジェクト正本 = PPTX）

`Ctrl+S`。中身は **path 参照 + プレビュー品質サムネイル + project part**。テキスト / 図形はベクタなのでフル品質。

```text
Save quality:  [ Preview 150 dpi ▾ ]   96 / 150 / 220 / Export-grade
Sources:       [ Linked ▾ ]            Linked / Portable
```

- 既定は `Preview 150 dpi` + `Linked`
- Portable は推定ファイルサイズを表示
- 保存後トースト：「プロジェクトとして保存しました（画像はプレビュー品質）。印刷・投稿用は Export を使ってください」

## 14.2 Export

`Ctrl+E`。Editable PPTX（フル解像度）/ PDF / PNG / TIFF。

- **目標 dpi を選ぶと px サイズと推定ファイルサイズを即時表示**、画像ごとの実効 dpi 一覧と警告を併記
- 非矩形テキスト領域の書き出し方（§7.5）をここで選択
- 欠損アセットが embedded preview で代替されている場合は赤警告して続行確認

Export は project-level source data を保証しない。

## 14.3 Print

別系統。physical page size / bleed / crop marks / DPI / transparency / vector preservation / font embedding を扱う。

PDF は依存追加（PDFBox 等、Apache-2）が必要になるため、S10 の着手時に「shade して JAR を数 MB 増やす / PPTX 経由に留める」を再判断する。それ以外は追加依存なしで実装できる。

---

# 15. PPTX as Project Container

PPTX を canonical project file として使用する。ただし **Save = フルエクスポートにはしない**。全 picture をフル解像度で焼くと `Ctrl+S` が毎回数十秒かかり編集リズムが壊れるため、正本には**プレビュー品質サムネイルだけを焼き付ける**。

## 15.1 パッケージ構成

```text
customXml/project.xml     … Document 全体（JSON）+ schemaVersion   ← 内容の正本
customXml/itemProps1.xml  … 固定 GUID（自分のファイルかの判別）
ppt/slides/slideN.xml     … Page N。shape 名に nodeId を埋め込む
ppt/media/…               … プレビュー品質サムネイル（派生物）
ppt/embeddings/…          … Portable 時のみ OME-TIFF
docProps/custom.xml       … SaveQuality = preview-150dpi 等
```

3 種類の情報を役割で分ける。

| 層 | 中身 | 品質 | 読み戻すか |
|---|---|---|---|
| project part | Document JSON（node 木・レイアウト式・style・asset の path と fingerprint・C/Z/T 選択・非矩形 flow region も含めて完全） | 無損失 | **これが正本。必ず読む** |
| shape tree | 幾何・テキスト・スケールバー・枠（ベクタ） | フル品質 | 差分検出と text / 色 / 塗り / 枠の取り込みのみ |
| media | 各ノードのラスタサムネイル | プレビュー（既定 150 dpi） | **内容としては読まない。** ソース欠損時の `Use embedded preview` としてのみ利用 |

- Save のコストはサムネイル生成だけ。A0 上の 60 mm パネルは 150 dpi で 354 px、30 枚でも 1 秒未満
- サムネイルは `(assetId, c/z/t, figureConfigHash, targetPx)` の fingerprint でキャッシュ・重複排除。未編集ノードは前回の media を zip エントリごとコピーして再利用
- テキスト・ラベル・スケールバー・inset 枠はベクタのままなので、PowerPoint で見ても文字がぼやけない（サムネイル品質は画像部分だけに効く）
- 既存 `PptxExporter` の「blank.pptx を読みながらエントリを差し替える」方式を拡張し、customXml part と `[Content_Types].xml` / rels を追加する。保存は temp → `Files.move` で原子的に行い、`OutputSafety` を必ず通す

## 15.2 副次的な利点

- 焼き付けサムネイルが §12.2 の `Use embedded preview` にそのまま使える
- PowerPoint の「図の圧縮」で media が再圧縮されても正本は壊れない（media は派生物なので無視できる）

## 15.3 プレビュー品質を成果物と誤認させない仕掛け

Linked での「正本」は厳密には PPTX 単体ではない（フル解像度で出し直すには元画像が必要）。サムネイルのおかげでソースが無くてもレイアウトを開いて見ることはできる、という graceful degradation の状態になる。1 ファイル完結が要るときだけ Portable を選ぶ。

誤用リスクは軽減できても消せないため、以下を仕様に入れる。

1. `Save` と `Export…` を名前・ショートカット・ダイアログで明確に分離
2. picture shape 名に品質を書く：`Figure 2 / Merge — preview 150 dpi`（PowerPoint の選択ウィンドウにそのまま出る）
3. ノート欄に定型注記：「この PPTX はプロジェクト正本です。画像はプレビュー品質のため、印刷・投稿には Fiji 側の Export を使ってください」
4. `docProps/custom.xml` に品質を記録し、reopen 時に status bar へ表示
5. `Save quality: Export-grade` を選べば、正本かつフル解像度の 1 ファイルにできる（遅いことを明示）

## 15.4 project part 消失時のフォールバック

PowerPoint は通常未知の customXml を保持するが、Web / モバイル PowerPoint や一部の変換経路で失われうる。開く時に part が無ければ:

- 「このファイルにはプロジェクト情報がありません」と明示
- shape tree から **読み取り専用の推定インポート**（Fixed 幾何 + テキスト + 画像）を提示
- 「別名で PPTX として保存し直すと編集可能になります」と誘導

設定で `.fpb` サイドカー（project.json のみ、数十 KB）の同時保存を任意で有効化できるようにする（正本は PPTX のまま）。

---

# 16. PowerPoint Round-trip

すべての exported object に stable ID を付与する。

```text
nodeId / contentId / assetId
```

reopen 時は **project part から Document を復元した上で**、shape tree を nodeId で突き合わせて差分を出す。project part が正本であり、shape tree は差分検出と限定的な取り込みにしか使わない。

| 種別 | 扱い |
|---|---|
| text / rich text / font / size / color / fill / border | **取り込む**（Node override として反映） |
| x / y / width / height | **原則レポートのみ。** 対象 Node の両辺が `Fixed` の時だけ取り込みを提案（`1fr` / `Auto` は逆算不能） |
| z-order | 同一親内で順序が保たれている場合のみ取り込み |
| 非矩形 flow を分割した複数テキストボックス（`nodeId#part1..N`） | **取り込まない・報告のみ**（分割された文字列を 1 段落へ戻す対応が一意でない） |
| 回転 / 反転 / skew / 自由変形 / グループ変換 / 親グリッド外への移動 | **検出して報告、取り込まない** |
| media の差し替え・PowerPoint の「図の圧縮」 | **無視**（media は派生物。次の Save で再生成される） |

reopen 時に以下のように明示する。

```text
PowerPoint edits detected

12 compatible changes imported
2 unsupported changes ignored

Unsupported:
- Figure A rotation: 13°
- Text block moved outside parent grid
```

なお PowerPoint 側で本文を直された場合、向こうの改行と我々の flow は一致しないため、取り込み後に overflow 警告が新たに出ることがある。これは異常ではなく reopen レポートに含めて見せる。

---

# 17. Role

`panel`, `figure`, `caption` 等の semantic role は必須にしない。必要なら optional metadata / tag として追加する。

```text
tags:
- methods
- caption
```

機能制限には使用しない。Named Style や Content type で大部分の意味は表現できる。

---

# 18. Project Versioning

最初から schema version を持つ。

```text
projectSchemaVersion = 1
```

将来 `v1 → v2 → v3` の migration を用意する。

加えて **既存 settings JSON (v1) の import 経路**を用意する（`LegacyImporter`）。既存ユーザーの資産を救済し、同時に「v1 を読み込んで新経路で描いた結果 == 旧 `PanelLayoutEngine` の出力」というピクセル比較の回帰テストが得られる。

---

# 19. UI / UX

## 19.1 Core principle

**内部能力は Excel / CSS / InDesign 級、表面UIは PowerPoint / Figma 級。** 高度な機能を最初から全部見せない。

## 19.2 ウィンドウ構成

```text
┌───────────────────────────────────────────────────────────────┐
│ Toolbar  ↶ ↷ │ + Image  + Text  + Container │ Split ▾ Merge   │
│          Page size ▾  Zoom ▾  Fit │ Save  Export ▾            │
├───────────────────────────────────────────────────────────────┤
│ Poster ▸ Results ▸ Figure 2 ▸ Merge          ← ブレッドクラム │
├────────┬──────────────────────────────────┬───────────────────┤
│ Pages  │                                  │ Inspector         │
│ ─────  │        Canvas (mm ルーラ)         │  Layout           │
│ Styles │                                  │  Appearance       │
│ (drag) │                                  │  Typography       │
│        │                                  │  Content          │
├────────┴──────────────────────────────────┴───────────────────┤
│ B&C dock（ScientificImage 選択時のみ／既存 ContrastPanel 再利用）│
├───────────────────────────────────────────────────────────────┤
│ Status: 選択パス │ 実効 dpi 210（警告） │ overflow 2 件 ▸      │
└───────────────────────────────────────────────────────────────┘
```

- 左ペインは既定で Styles のみ展開、Outline ツリーは折りたたみ
- Inspector は選択対象が何であっても **常に Layout / Appearance / Typography / Content の 4 節**。不要な節だけ隠す。Poster / Panel / Figure ごとに別設定画面を作らない
- 起動は既存 Figure モードと**別コマンド**（`Plugins > Figure Panel Builder > Poster / Layout`）。既存 `Plugins > Figure Panel Builder` は無変更

## 19.3 再帰モデルで迷子にならないための仕掛け

「すべてが Node」の最大の UX リスクは、どの階層を選んでいるか分からなくなること。

- **ブレッドクラム**。クリックで祖先へジャンプ
- **Esc = 親を選択**、**Enter / ダブルクリック = 子へ入る**、**Tab = 次の兄弟**
- 選択枠は階層で色を変え、親の輪郭を薄く同時表示
- **既定でラッパを作らない**：Grid に画像を落としても中間 Node を挿入しない。Container は明示操作（Wrap in container）でのみ生まれる

## 19.4 Canvas 直接操作

| 操作 | 結果 |
|---|---|
| クリック | 選択（ScientificImage なら B&C dock を開く） |
| ダブルクリック | 子へ入る／Text なら編集開始 |
| ドラッグ（セル間） | Grid 内の移動・入れ替え（既存 `FigureWorkspace` のドラッグ並べ替え体験を踏襲） |
| ドラッグ（トラック境界） | `Fraction` 比率の変更。tooltip に mm と比を表示 |
| 端のハンドル | Fixed 化してリサイズ（Shift = アスペクト維持、Alt = 対称） |
| 右クリック | Split H/V・Merge cells・Wrap in container・Unwrap・重ね合わせに変換・Bring forward / Send backward |
| Ctrl+ホイール / Space ドラッグ | ズーム / パン |
| Style を drop | そのノードへ適用（hover ハイライト、Alt で親へ） |
| ゴミ箱 or Delete | Node 削除（**元画像・Fiji 上の画像には触らない**） |

## 19.5 キーバインド

`Ctrl+Z / Y` Undo / Redo・`Ctrl+S` Save・`Ctrl+E` Export・`Esc` 親へ・`Enter` 子へ・`Tab` 兄弟・`Ctrl+G` Wrap in container・`Ctrl+Shift+G` Unwrap・`Ctrl+D` 複製・`Ctrl+0` Fit・`Ctrl+1` 100%。

## 19.6 Advanced の隠し方

通常:

```text
Width     Fill
Height    Auto
Padding   8 mm
Gap       6 mm
Style     Card
```

Advanced:

```text
Min width  80 mm
Max width  160 mm
Weight     2fr
Same as    sibling A
```

数式欄は初回リリースでは表示しない。

---

# 20. Implementation Order

すべて初回リリースに含む。以下は依存順の内部区切り。

| S | 内容 | 完了判定 |
|---|---|---|
| S1 | Document / Page / Node / Content モデル・Gson シリアライズ・schemaVersion・`LegacyImporter` | v1 settings を読み込んで Document になる |
| S2 | `LayoutEngine` + `LayoutResult` + SizeExpr 閉じた集合 + 解決順序 | プロパティテスト（トラック合計 == 親内寸、Aspect 保持）が通る |
| S3 | `RenderTarget` / dpi / `NodeRenderer` / `TileRasterizer` + `ImageRenderer` 高速化 | A0 @ 300 dpi PNG が上限エラーなしで書ける |
| S4 | Canvas + Inspector + ブレッドクラム + 直接操作（§19.4） | 入れ子 Grid をマウスだけで組める |
| S5 | `ScientificImageContent`（既存 FigureConfiguration ラップ）+ `AssetLibrary` + Bio-Formats(provided) + relink | LIF の series 2 を配置して C/Z/T を切り替えられる |
| S6 | Text：rich text・FlowRegion・島順序・overflow UX | L 字領域に本文が流れ、overflow が警告される |
| S7 | Token / Style / StyleResolver / Style Manager（drag & drop） | 「サイズ共通・フォント別」が Token で表現できる |
| S8 | PPTX 正本の書き出し（サムネイル方式・Linked / Portable）+ サムネイルキャッシュ + reopen + フォールバック | 30 パネルのポスターの Save が 1 秒未満。保存 → PowerPoint で開く → 再 open で同一レイアウト |
| S9 | PowerPoint 差分検出・text / style 取り込み・報告ダイアログ | §16 の表通りに分類・報告される |
| S10 | Export：PDF（ベクタ + フォント埋め込み）/ PNG / TIFF / 印刷設定（bleed・trim mark・dpi） | A0 PDF が印刷所要件で出る |
| S11 | 仕上げ：性能（タイルキャッシュ・非同期プレビュー）・Undo coalescing・ja/en・ドキュメント・検証記録 | VALIDATION.md 更新、Free mode を deprecated 表示 |

---

# 21. Verification

- `powershell -File build.ps1`（`mvn verify`）を各 S の完了時に実行。既存テストを壊さない
- **回帰の要**：`LegacyImporter` で v1 settings を読み、新経路で描いた結果を旧 `PanelLayoutEngine.render` の出力とピクセル比較（許容差 0）。`test-data/example-settings.json` で自動化
- レイアウトのプロパティテスト：トラック合計・Aspect 保持・Min/Max クランプ・SameAs 収束
- OOXML 検証：生成 PPTX を unzip して XML パース、`p:sldSz` が mm × 36000 と一致、shape 名に nodeId が入っている、`customXml/project.xml` が復元して Document と等価（往復同型）であることを assert
- サムネイル方式の検証
  - Save → reopen → Save が Document を変えない（冪等）
  - media を全部壊した PPTX でも reopen 後に元ソースから正しく再描画できる（media が正本でないことの証明）
  - 30 パネル相当の合成ポスターで Save が既定品質で 1 秒未満
  - 2 回目の Save で未編集ノードの media がバイト一致（キャッシュ再利用の確認）
- ラスタ検証：ゴールデン画像（小サイズ）+ タイル分割を変えても同一出力になること（継ぎ目の検査）
- GUI：既存の `*UiValidation` main クラス方式を踏襲して `PosterUiValidation` を追加（`artifacts/` にスクリーンショット、Git 管理外）。ユーザーの既存 Fiji プロセスには触れず別プロセスで確認
- 手動チェックリスト：Windows PowerPoint で開く／編集して再 open／A0 PDF を印刷所プリフライト／CJK フォント未インストール環境での代替解決

---

# 22. Risks

| リスク | 対策 |
|---|---|
| ポスター全体のラスタ化でメモリ破綻 | §6.1 タイル方式。Document level の MP 上限を撤廃、Content level に残す |
| プレビュー品質の正本 PPTX を成果物と誤認して印刷・投稿してしまう | §15.3 の 5 つの仕掛け（Save/Export 分離・shape 名の dpi 表記・ノート注記・docProps 記録と status bar 表示・Export-grade オプション） |
| Save が遅くて編集リズムが壊れる | §15.1 サムネイル方式 + 未編集ノードの media 再利用。S8 の完了判定に「30 パネルで 1 秒未満」を入れる |
| PPTX 正本の project part 消失 | §15.4 フォールバック + 任意 `.fpb` サイドカー |
| Portable でファイル肥大 | 埋め込みは選択 series の OME-TIFF のみ（rendered はサムネイルなので二重保存にならない）。保存前にサイズ見積り表示 |
| 非矩形テキストが PPTX で再現できない | §7.5 で書き出し方式を明示的に選ばせる。既定は矩形分割 |
| Z/T / 多 series でメモリ破綻 | §12.1 遅延 plane 供給 + LRU |
| Bio-Formats のライセンス・可用性 | `provided` + reflection + 未検出時 degrade |
| 「すべて Node」で操作が迷子 | §19.3 ブレッドクラム・Esc/Enter・既定でラッパを作らない |
| 既存 Figure モードの退行 | 新パッケージ・別コマンドで完全分離。既存テストを不変条件として維持 |
| 科学的正確性（勝手な補間・実効 dpi 不足） | §6.2 リサンプル方式の明示・dpi 警告・native 等倍モード。非破壊は既存方針を継承 |

---

# 23. Design Principles to Preserve

1. **相似形** — どの階層でも同じ Node capability
2. **Grid-first** — freeform layout は補助。arbitrary placement を中心にしない
3. **Progressive disclosure** — advanced capability は隠す
4. **Style by reference** — literal と Token 参照を property 単位で混在可能
5. **Source fidelity** — Scientific source data と rendered preview を分離
6. **Portable when needed** — 普段は Linked、必要時だけ TIFF-based Portable
7. **PowerPoint-compatible, not PowerPoint-clone** — supported subset を明確化
8. **Non-destructive** — 元画像データを変更しない
9. **Versioned document** — schema migration 前提
10. **Single coherent architecture** — Poster / Panel / Figure 専用機構を増やさず、Node + Layout + Content + Style に集約
11. **Single source of layout truth** — 座標計算を `LayoutResult` 1 箇所に集約し、canvas / hit test / exporter で重複させない
12. **Physical units are real** — mm は正確に mm。EMU / pt / px への変換は決められた 1 か所で行う
13. **Resolution is explicit** — 実効 dpi を常に計算して見せ、暗黙のリサンプルをしない
14. **Save is cheap, Export is expensive** — 正本の保存は即時。フル解像度は明示的な出力操作でのみ発生する

---

# Appendix. 前版（rev.1）からの主な変更点

| # | rev.1 | rev.2 |
|---|---|---|
| 1 | 単位は mm（変換規則は未定義） | mm 論理 + 1 mm = 36000 EMU で整数スナップ。長辺正規化を廃止（§3.1） |
| 2 | Document → Root Node 1 本 | Document → Pages[] → Root Node（§2.1） |
| 3 | SizeExpression に Reference / Formula | 閉じた集合に確定し **AspectRatio / Min / Max を追加**、Formula は延期（§5.1） |
| 4 | dependency graph + 循環拒否 | 順序解決に置き換え。SameAs は兄弟限定で循環を構造的に排除（§5.2） |
| 5 | （記述なし） | `LayoutResult` を単一の真実に（§2.3） |
| 6 | 解像度は Phase 9 の印刷項目 | **DPI をコアの一級概念に**（§6） |
| 7 | 100 MP 上限（既存踏襲） | Document level では撤廃し、タイル逐次出力へ（§6.1） |
| 8 | Style property が他 Style の同 property を参照 | **Token → Style → Node の 2 段**に。循環検出が不要（§8） |
| 9 | 非矩形 flow（PPTX との整合は未記述） | 実装方針を確定し、**PPTX の矩形制約と書き出し方式を明示**（§7.5） |
| 10 | 幾何は「一意に戻せる場合のみ import」 | **project part 正本 + 差分レポート**を既定に反転（§16） |
| 11 | Asset DB（読み方は未記述） | 遅延 plane 供給 + LRU。開いている画像は即時スナップショット（§12.1） |
| 12 | Bio-Formats 対応 | `provided` スコープ + reflection + degrade（§13.1） |
| 13 | （記述なし） | ImageRenderer 高速化（DataBufferInt 直書き・LUT テーブル・面積平均）（§6.3） |
| 14 | schema version のみ | 既存 v1 settings の import 経路を追加し回帰テストに使う（§18） |
| 15 | Canvas Size は Auto / A0 / A1 / Custom | Journal column preset を追加（§3.2） |
| 16 | PPTX 正本（保存と出力の区別なし） | **Save = path 参照 + プレビュー品質サムネイル + project part**、フル解像度は Export（§14・§15） |
| 17 | Latin / CJK 分離 | Style binding 化 + fallback + 未インストール警告（§9） |
| 18 | UI 原則のみ | UX を §19 として具体化（構成・ブレッドクラム・操作一覧・キーバインド） |
