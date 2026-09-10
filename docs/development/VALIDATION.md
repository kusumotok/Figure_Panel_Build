# 検証結果

## 2026-09-08 Figure直接操作・Undo/Redo

- Gitは初期化済み・コミットなしだったため、既存ソース／設定例をbaseline `f63f429`として保存。既存ファイルのreset/revert/deleteは行っていない。履歴管理を`c741d3b`、主要UI変更を`a13dd1f`で段階的にコミット。
- 前回の補助ライブラリ復旧結果を確認。同一版26.905.11957の公式アーカイブと7,796ファイルの照合・Artifact Tool import成功を確認済み（artifacts/runtime-recovery/restoration.json）。今回の作業では増分ビルドだけを使用。build.ps1にはclean前のリンク／ジャンクション検査を追加。
- 最終ビルド成功、JUnit 30テスト、失敗0・エラー0。履歴の独立性・連続編集の集約・Undo後の分岐・空レイアウトと初期化状態の復元を追加検証。
- `DirectManipulationValidation`を既存Fijiとは別のJVMで実行。合成Control/HPR/KO画像を使用し、以下を実Swingウィンドウで確認。

| 確認内容 | 結果 |
|---|---|
| 起動直後のSelect Images、上部Select Images、＋Condition | 同一選択UIで画像追加成功、キャンセル成功 |
| ＋Channel / Merge | 単独追加・3チャネルmerge追加成功 |
| Condition / Channel並べ替え、Swap | ドラッグと両軸の対応を確認 |
| ドラッグ表示 | 対象の半透明＋枠、挿入先ライン、ゴミ箱hoverの強調を目視確認 |
| ゴミ箱Drop | 通常配置・軸交換後のCondition/Channel/Merge除外、確認・キャンセル成功 |
| 全Condition削除後 | Channel表の選択でB&Cのnull参照が起きないこと、Undoで最後のConditionが復元されることを確認 |
| Undo / Redo | Ctrl+Z/Ctrl+Yの登録キーからActionを起動し、追加・削除・名前・B&Cなどを復元。上部アイコンも同じ処理を使用 |
| 名前・LUT・B&C・Invert gray | 元チャネルの共通設定に反映し、mergeの名前も追随 |
| Label / Scale bar | Style画面で変更し、Undo/Redo・設定再読込後も保持 |
| Generate TIF | 従来と同じ24-bit RGB ImagePlusを生成 |
| Save RGB TIFF / PNG / PPTX | 実際の保存ボタンとファイル選択画面から保存成功 |
| Save settings / Load settings | 相対ソースパスを含むJSON保存・再読込成功、構成とStyleを保持 |
| 元TIFの保護 | 3ファイルが残存し、操作前後のSHA-256一致 |
| 右ペインと画面サイズ | Label nameの外側スクロール不要。最大化・1280×800で両表と操作ボタン、B&C、ゴミ箱の収まりを確認 |

最終実行出力: `artifacts/direct-ui-5112190191842314320/`。画面、ドラッグ中の画像、TIFF/PNG/PPTX、設定JSONを保存。テストはアプリ内のイベントとキー割当経路を使用し、ユーザーの既存Fijiウィンドウには接続していない。

主な変更ファイル:

- `FigurePanelBuilderDialog.java`: 共通画像選択、起動時選択、表／タブ／ツールバー整理、削除確認、履歴UI連携。
- `FigureWorkspace.java`: Figure内Swap、ドラッグ対象と挿入先表示、ゴミ箱Drop、Escキャンセル。
- `ContrastPanel.java`: Invert grayと履歴に使う対象チャネル情報。
- `FigureHistory.java`: 最大100操作の設定履歴、不変の画像スナップショットの共有。
- `FigureIcons.java` / `TrashTarget.java`: Swap・Undo/Redo・ゴミ箱のアイコンとDrop強調。
- `FigureHistoryTest.java` / `DirectManipulationValidation.java` / 既存UI検証2ファイル: 履歴・操作・保存の確認。
- `.gitignore` / `AGENTS.md` / `build.ps1` / `README.md` / `VALIDATION.md`: Git運用、削除事故防止、操作説明と検証記録。

## 2026-09-08 最大化・透過・編集可能PPTX

- 最終ビルド成功、27テスト、失敗0・エラー0。
- 1024px画像の初期値: フォント102px、バー長20µm（0.25µm/pxなら80px）、幅31px。既存の手動値・JSON設定は維持。
- 実Swingウィンドウで最大化状態、下部保存ボタンの表示、3択背景ボタン、ラベル選択枠、ヒストグラムのないB&Cを確認。
- PNGの余白alpha=0、画像セルalpha=255。白背景のGreen/Yellow/Cyan、黒背景のBlueのラベル色と画像LUTの独立性を検証。
- PPTX内の元解像度画像、ネイティブ文字・バー図形、mergeの色付き文字、Unicode/XML特殊文字、軸交換、透過時の背景塗り省略を検証。
- 黒背景・背景塗りなしの1枚スライドを構造検査し、Artifact Tool再読込・レンダリング成功。回転行ラベルの回転前外接矩形に境界警告が出るが、描画された文字はスライド内に収まることを確認。
- PowerPointでeditable-figure-validated.pptxを開き、修復警告なしで表示。画像、行列ラベル、バー、バー文字が個別オブジェクトとして認識され、行ラベルのみ選択できることを確認。
- 例: artifacts/editable-figure-validated.pptx、artifacts/transparent-figure.png。合成テスト画像を使用。


## 2026-09-08 画像サイズ連動・Labels / Scale再構成

- ビルド成功、25テスト、失敗0・エラー0。
- 1024px画像でラベル／バー文字102px、バー幅10px、校正0.25µm/pxでバー長25.5µm（102px）を確認。
- 行・列の独立した白／黒設定、旧JSONの共通文字色との互換性、四辺・軸交換時のラベル選択枠、出力画像への枠の非混入を検証。
- AppearanceUiValidationで実ウィンドウの1024px TIFF読込、初期値、ラベル枠、4グループの設定画面を確認。手動フォントサイズ77pxが2枚目の追加・JSON再読込後も保持されることを確認。
- 画面: artifacts/appearance-workspace-1024.png、設定全体: artifacts/appearance-controls.png。

## 2026-09-08 UI更新

- `build.ps1`成功。22テスト、失敗0・エラー0。
- 長いmerge名の自動縮小、ラベルの描画領域、設定フォントサイズの保持を検証。
- 行・列交換時の＋タイルの追加対象、ラベル四辺のヒットテスト、余白とセル間Gapの除外、クリックとドラッグのイベント、古いプレビューへの操作無効化を検証。
- B&CのBrightnessが表示範囲幅を維持し、Contrastが表示範囲中心を維持すること、選択した元チャネルだけを更新すること、LUT変更・Resetを検証。
- `WorkspaceUiValidation`で実Swingウィンドウを表示。＋タイル→チェックボックス選択→3チャネルmerge作成、設定JSON読込、軸交換、B&C開閉、設定パネル表示を検証。
- `artifacts/workspace-*.png`に単独チャネル、merge、7条件、軸交換、B&C非表示、設定パネルの画面を保存。合成画像によるレイアウト検証で、ユーザー添付の顕微鏡写真はテストデータとして取り込んでいません。
- 以前のFiji MCPによるレンダラー検証記録は`artifacts/FIJI-MCP-VALIDATION.md`を参照。

2026-09-07、Windows、Fiji付属Zulu JDK 21.0.7、Maven 3.9.9。

| 段階 | 結果 |
|---|---|
| Phase 1: SciJava skeleton | compile成功 |
| Phase 1: 入力・モデル・レンダラー | compile成功 |
| Phase 1: GUI・RGB出力・コアテスト | verify成功、5 tests |
| Phase 2: ラベル・Gap・スケールバー | verify成功、8 tests |
| Phase 3: Preview・JSON・Histogram・UI | verify成功、11 tests |
| 最終: 非破壊・UI連動の追加検証 | verify成功、16 tests、失敗0・エラー0 |

ログ: phase1-verify.log、phase2-verify.log、phase3-verify.log、final-verify.log。JUnit詳細: target/surefire-reports。

## 確認した内容

- 7条件×Green/Red/Merge→3×7、軸交換、手動不一致拒否
- 共通画素1000の出力RGB一致、additive Merge、7 LUT、invert、clamp
- 全条件のAuto min/maxとHistogramの画素総数
- 全Channel元画素、LUT、表示範囲、ROI、Overlay、Channel位置、校正の保持
- 元画像変更後も取り込んだコピーが独立していること
- 処理前後で元TIFFのSHA-256一致
- 開画像の元パスを用いた保存先保護、設定保存による元TIF上書き拒否
- Z/T拒否、サイズ不一致、Channel参照不整合、非有限B&C、設定version不整合
- 校正µm/nm、未校正時の手入力、バー適用範囲、セルに入らないバー拒否
- ラベル領域・Gap寸法、左右上下配置
- 3枚のTIFF読込、C=3/Z=1/T=1/16-bit確認、JSON往復、24-bit RGB TIFF再読込
- SwingスライダーがChannel共通設定だけを変更すること、Gapの連動
- SciJavaのMETA-INF/json/org.scijava.plugin.Pluginにメニュー登録生成を確認
- artifacts/example-figure.pngの3×3配置・ラベル・バーを目視確認
- Swing UIのオフスクリーン描画確認。実際のFijiメニュー起動・ファイルダイアログ・ドラッグ操作の手動E2Eテストは未実施

実装の対象外と操作上の制約はREADME.mdに記載。

## 2026-09-09: Claude 版を基準に整理・保存先を修正
- 削除前の Old Version と New Version のソース・文書を `552fd12` に保存し、Old Version の追跡対象が HEAD と一致することを削除前に確認。
- 管理外の旧成果物・ログ・依存ツールはルートの `archive/old-generated/` に保全。New Version の以前の配布物は `archive/previous-new-dist/` に保全。
- Claude のレイアウト・初期値・LUT・Inset の変更を継承。今回の製品コード変更はファイル選択の初期フォルダに限定。
- 通常モード / Free build: 選択画像 → 図の使用画像 → Fiji の現在画像 → 標準フォルダの順に解決。TIFF / PNG / PPTX / 設定保存 / 設定読み込みに適用。
- `build.ps1` verify: 46 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS。
- 新規回帰テスト: 選択画像の優先順位、無効な親フォルダのフォールバック、未保存画像。
- 実際のネイティブファイルダイアログの手動操作は未実施。既存 Fiji プロセスおよび入力画像は変更していない。
- 当時の JAR: `New Version/dist/figure-panel-builder-1.0.0.jar`。フォルダ整理後の配布先は `dist/figure-panel-builder-1.0.0.jar`。

## 2026-09-09: フォルダ整理後の配布・サンプル導線の修正

- `dist/` 全体の除外を見直し、配布 JAR 1 ファイルだけを Git 管理対象に変更。README に直接ダウンロードリンクと配布更新手順を追加。
- 設定 JSON と合成 TIFF 3 枚のダウンロードリンク、同じフォルダに保存する手順、ZIP 一括取得を追加。サンプルと設定形式は変更なし。
- `artifacts/` 内の過去の UI スクリーンショット・合成画像・生成フィギュア（PNG/TIFF 計48ファイル）を削除。実体パスとリンクの有無を確認し、画像ファイルだけを個別に削除。`test-data/` の入力 TIFF 3 枚は保持。
- この文書の過去の `artifacts/` パスは実行当時の記録であり、画像の現存・配布を保証しない。`archive/` はユーザーによる整理で削除済み。Git 管理外だった出力は Git 履歴からは復元できない。
- 製品コード・設定 JSON・画像出力処理は変更なし。ユーザーの Fiji プロセス・開画像には接続していない。
- `build.ps1` の増分 verify 成功: 46 tests, 0 failures, 0 errors, 0 skipped。初回は依存 JAR のアクセス制限で失敗し、権限付き再実行で成功。ビルド JAR と配布 JAR の SHA-256 一致、サンプル JSON/TIFF の Git 差分なし、README 類のローカルリンク先と配布用 raw リンクの対応ファイルの存在を確認。GitHub 上の更新後のダウンロード確認は push 後に必要。

## 2026-09-09: サンプル名の統一と ZIP 配布

- Control → Image A、HPR → Image B、KO → Image C にファイル名・設定例のラベルと相対パス・テストと生成コードを統一。TIFF は再生成せず名前だけ変更し、変更前後の SHA-256 一致を確認。
- `dist/figure_panel_builder_testset.zip` に設定 JSON と TIFF 3 枚を同じ階層で格納。README の主手順を ZIP のダウンロード・展開・設定読込に変更。`package-testset.ps1` で現在のサンプルから再作成可能。
- ZIP 内の4ファイルが元ファイルと SHA-256 一致、JSON の全参照先が ZIP 内に存在することを確認。
- 増分 `build.ps1` verify 成功: 46 tests, 0 failures, 0 errors, 0 skipped。製品コード・配布 JAR は変更なし。既存 Fiji プロセス・開画像の操作なし。

## 2026-09-10: Poster / Layout 基盤（S1-S3, S8）

新パッケージ `org.microscopy.panel` を追加。既存 `org.microscopy.figure` は無改変で、既存 46 テストは全通過を維持（合計 92 tests, 0 failures, 0 errors）。

### 実装した範囲

| 段階 | 内容 |
|---|---|
| S1 | Document / Page / Node / Content モデル、Gson 往復、schemaVersion、`LegacyImporter`（v1 settings JSON → Document） |
| S2 | `LayoutEngine` + `LayoutResult`、SizeExpr（Auto / Fixed / fr / % / SameAs / AspectRatio / Min-Max）、順序解決 |
| S3 | `RenderTarget`（dpi）、`NodeRenderer`、`TextRenderer`（LineBreakMeasurer）、`DocumentRasterizer`（バンド分割）、`PngStreamWriter`、`ResolutionReport` |
| S8 | `PptxProjectWriter` / `PptxProjectReader`（path 参照 + プレビュー品質サムネイル + project part） |

### 自動検証（`mvn verify`）

| 確認内容 | 結果 |
|---|---|
| 単位変換 | 1 mm = 36000 EMU 厳密。A0 = 30276000 x 42804000 EMU。長辺正規化は廃止 |
| レイアウト | トラック合計＝内寸、fr 配分、clamp 時の凍結と再配分、span のギャップ込み幅、AspectRatio 保持、SameAs の順序非依存 |
| SameAs 循環 | `Document.validate()` が保存前に拒否（レイアウト時ではなく作成時） |
| v1 取り込み | 3x3 図が 9 セル + 行/列ラベル 3+3 ノードに分解。ラベル帯の左右上下、Merge ラベルのチャネル別色、scale bar scope のセル単位解決 |
| **旧エンジンとのピクセル一致** | 取り込んだ 9 セルすべてが `PanelLayoutEngine.render` の該当領域と完全一致（許容差 0） |
| mm 丸めの整合 | dpi = 25.4/mmPerPx で 820x628 px、各セルの px 位置・サイズが旧計算式と一致 |
| バンド分割の不変性 | バンド 7 行と 256 行で出力が完全一致（継ぎ目なし） |
| ストリーミング PNG | 書き出し → ImageIO 読み戻しが完全一致 |
| OOXML | 全 xml / rels パートが well-formed。`p:sldSz` が mm x 36000 と一致。各 shape に nodeId |
| project part | JSON 往復が完全一致。Save → reopen → Save が不動点 |
| media が派生物であること | 全 media を破壊した PPTX でも project part から再 open し、ソースから再描画 |
| media 再利用 | 無変更での 2 回目の保存は 0 レンダリング・全件再利用。サイズ不一致/非 PNG は再利用せず再描画 |
| 複数ページ | 2 ページ → slide1/slide2、Content_Types・presentation rels・sldIdLst を整合 |
| project part なしの PPTX | 例外ではなく「読み取り専用インポート＋再保存で編集可能」の案内を返す |

### 実測（`PosterRenderValidation`, A0 縦・6 パネル、JDK 21 / -Xmx1g）

```
page   : 841 x 1189 mm
raster : 9933 x 14043 px = 139.5 megapixels
wrote  : a0-poster.png (12.7 MB) in 3.7 s      ← Export 相当
heap   : 490 MB used of 1074 MB max            ← 全体ラスタなら 558 MB を単一配列で要求
save   : a0-poster.pptx (2.6 MB) in 1.46 s, 6 media rendered   ← Save（プレビュー 150 dpi）
resave : 0.14 s, 0 rendered / 6 reused
```

139.5 MP は既存の 100 MP 上限では拒否される規模。Document level の上限を撤廃しバンド分割にしたことで、A0 300 dpi が 1 GB ヒープで通る。Save と Export の分離により、保存は再保存 0.14 秒。

出力は `artifacts/`（Git 管理外）: `a0-poster.png`、`a0-poster-preview.png`、`a0-poster.pptx`。

### 未実施・残課題

- **PowerPoint 実機での開封確認が未実施**（Windows PowerPoint での表示・テキスト編集・再 open）。`artifacts/a0-poster.pptx` で要確認。
- S4 Canvas/Inspector UI、S5 Asset DB と Bio-Formats、S6 テキスト flow region と overflow、S7 Token/Style 解決、S9 PowerPoint 差分取り込み、S10 PDF/TIFF/印刷、S11 仕上げは未着手。
- `TextRenderer` は矩形領域のみ。非矩形 flow region は S6。
- `NodeRenderer` / `PptxShapeBuilder` は appearance のリテラル値のみ解決（Token 参照は S7 で結線）。
- TIFF 出力はストリーミング未対応（S10 で ImageJ TiffEncoder 経路）。

## 2026-09-10 (2): Poster / Layout の入口と Canvas / Inspector（S4 前半）

`Plugins > Poster / Layout Builder` を追加。既存 `Plugins > Figure Panel Builder` はメニューパスも実装も無変更（同名の leaf とサブメニューの衝突を避けるため兄弟項目にした）。合計 94 tests, 0 failures, 0 errors。

### 追加した要素

`PosterCommand` / `PosterFrame` / `PosterCanvas` / `Inspector` / `AssetLibrary`。

`AssetLibrary` は S5 の先取りで、assetId → 画素の解決を一箇所に集約する。ファイル由来は初回描画時に遅延読み込み、Fiji で開いている画像は従来通り即時スナップショットを登録。欠損時は `missing()` を返し、UI が再リンクを促す。既存 `org.microscopy.figure` には手を入れていない。

### GUI 検証（`PosterUiValidation`、既存 Fiji とは別プロセス）

| 確認内容 | 結果 |
|---|---|
| A1 ポスター（3x2 パネル + タイトル + キャプション）の表示 | ok |
| ステータスバー | `594 x 841 mm | 6 of 6 pictures are below 300 dpi (6 below 150 dpi).` |
| パネル選択 | 選択枠と親の薄い輪郭を描画、パンくずが `Poster ▸ Panels ▸ Image A Green` |
| Esc / Enter / Tab | 親へ / 子へ / 次の兄弟へ移動 |
| 列を 1fr → 2fr | 279.0 mm → 372.0 mm（x1.333 = (2/3)/(1/2)、期待値と一致） |
| Save project | PPTX 1 スライド、プレビュー品質でサイズ小 |
| Export PNG 300 dpi | 書き出し成功 |
| 自身が保存した PPTX の再 open | 6 パネルを復元 |
| 元 TIFF の保護 | 操作前後の SHA-256 一致 |

スクリーンショット: `artifacts/poster-ui/`（Git 管理外）。

### 検証で見つけて直した不具合

1. **アスペクト固定の子が引き伸ばされた行の上端に寄る** → STRETCH でセルを埋めきれない子は中央寄せに変更。ぴったり収まる場合は無変化なので、旧エンジンとのピクセル一致テストは影響を受けない。
2. **行の Auto 計測が解決後の列幅を見ていない** → 列 2fr にするとアスペクト由来の高さが行高を超え、3 件のクリップ警告と画像切れが発生していた。CSS grid と同じく列を先に解決し、その幅で行を計測するよう修正（`SizeExpr.FRACTION` は与えられた領域を埋める、という定義も明示）。
3. **トラック合計が親を超えても無警告** → 「rows need 160 mm but only 100 mm is available」のように報告するよう追加。固定ページ + 2fr 列 + アスペクト固定は容易に過剰制約になるため、黙って歪めず言う方針を維持。

いずれも回帰テストを追加済み（`LayoutEngineTest`）。

### 未実施

- **Fiji 実機での確認が未実施。** `C:\Program Files\Fiji\plugins` は BUILTIN\Users が読み取り専用のため、管理者権限での jar コピーが必要。コピー後に Fiji MCP で `Plugins > Poster / Layout Builder` の起動確認を行う。
- キャンバス上のドラッグ編集（分割・結合・トラック境界ドラッグ）は未実装。現時点の編集は Inspector の数値入力のみ。

## 2026-09-10 (3): Composite asset と Bio-Formats 取り込み（S5）

合計 103 tests, 0 failures, 0 errors。既存 `org.microscopy.figure` は引き続き無変更。

### 設計判断：composite asset

実データ（Leica lif）が「1 series = 1 蛍光チャネル、3 series で 1 視野」だったため、**series をまたいだ Merge** が必須と判明。既存 `FigureConfiguration` は「1 セル = 1 ソース」前提なので、それを壊さずに実現するため **composite asset**（複数ソースの各 1 チャネルを束ねた仮想アセット）を導入した。レンダラからは通常の多チャネルソースに見えるため、B&C・LUT・Merge・スケールバー・Inset の実装が一切変わらない。

- composite は 1 段のみ（composite の composite を禁止）
- 全 part が同じピクセルグリッドであることを `Document.validate()` で検査
- part 側の calibration を継承（合成画像でもスケールバーが引ける）

Bio-Formats は **reflection 経由**（Fiji 同梱・GPL、依存に加えるとオフラインビルドとライセンス境界が崩れる）。未検出時は「Fiji 内で実行するか TIFF に変換」と案内して degrade。

`AssetLibrary` にバイト予算つき LRU を追加（既定 512 MB）。Fiji で開いている画像は退避不可なので pin、ファイル由来のみ evict。

### 実データ検証（`ContainerImportValidation`、285 MB / 34 series の lif）

```
listed 34 series in 0.58 s          ← メタデータのみ
composite: 2 series -> C=2 2048x2048, assembled in 0.60 s
rendered 1024x1024 merge in 0.12 s
placing 14 images of 2 channels
imported and laid out in 3.3 s      ← 28 series の復号 + オートレンジ含む
status: 180 x 180 mm | All 14 pictures reach 300 dpi or better.
saved project in 0.3 s (1.4 MB)
exported 300 dpi PNG in 0.3 s (5.9 MB)
```

2048 px を 41 mm に配置すると約 1270 dpi になるため、合成 TIFF（256 px）のときと違い解像度警告が出ないことも確認できた。出力は `artifacts/container/`（Git 管理外）。

### 検証で見つけて直した不具合

**measure パスで列が未解決のまま行を計測していた。** Fill の子に幅が渡らず、300 dpi 相当の固有サイズ（2048 px = 173 mm）にフォールバックして行高が決まり、ページが 180 x 538 mm に膨張していた（正しくは 180 x 180 mm）。幅が既知のときは measure でも列を配分してから行を測るよう修正。回帰テスト追加済み。

### 実データから分かった運用上の注意

最初 3 series ずつでグループ化して検証したところ、パネルごとに色構成がばらついた。**このファイルの正しい構成は 2ch x 14 視野**（ユーザー確認済み）で、単にグループサイズを間違えていたのが原因。`x` の付いた 6 本は視野まるごとの失敗撮影なので、除外後の 28 本を 2 本ずつにすると余りゼロでちょうど 14 視野になり、各パネルの 2 チャネルが同一視野で揃うことを目視で確認した。

ただし「1 視野のうち 1 チャネルだけ失敗」という状況では、除外後に順番でグループ化すると**以降の位相がずれて別視野のチャネルが 1 枚に混ざる**。これは規則で推測できないため、取り込みダイアログに **Becomes 列**（各 series がどの画像のどのチャネルになるか）を表示して実行前に確認・修正できるようにした。`ContainerImportTest` で位相ずれの発生条件と、視野ごとまとめて失敗した場合は位相が保たれることを両方固定している。

### 未実施

- 取り込みダイアログの実機操作確認（jar 再インストールが必要）
- multi-channel / multi-Z / multi-T を含む lif での検証（手元のファイルは全 series が C=1 Z=1 T=1）
- Portable（選択 series の OME-TIFF 埋め込み）

## 2026-09-10 (4): B&C ドック（既存 ContrastPanel の再利用）

103 tests, 0 failures, 0 errors。

ScientificImage ノードを選択したときだけ下部に既存 `ContrastPanel` を出すようにした。チャネル選択・LUT・Grayscale・Invert gray・Min/Max・Auto/Reset・4 本のスライダー・ヒストグラムがそのまま使える。`setIndividual()`（Free build 用に既にあったもの）を呼んで「shared across all conditions」の文言を「selected panel only」に切り替えている。Poster では各ノードが独立した `FigureConfiguration` を持つため。

`org.microscopy.figure` への変更は `InputImageManager.adopt(String id, Source)` の**追加のみ**。既存コードからは呼ばれず、既存 46 テストは全通過。Document がアセット ID で画素を参照するのに対し、`ContrastPanel` は `inputs.get(sourceId)` で引くため、その橋渡しに必要だった。

スライダー操作中にパネルが作り直されないよう、選択ノードが変わったときだけ再構築する。

### GUI 検証で直したもの

**スクリーンショットが前面の別ウィンドウを写し込んでいた。** `Robot.createScreenCapture` は画面の矩形を撮るため、Poster ウィンドウの手前にあったものが混入した（実際にユーザーのブラウザ画面が入り、当該ファイルは削除済み）。`frame.paint(Graphics)` でウィンドウ自体を描画する方式に変更。前面状態に依存せず、無関係な画面内容も入らない。

## 2026-09-10 (5): キャンバス直接操作（S4 後半）

113 tests, 0 failures, 0 errors。

`LayoutResult` にトラック幾何（`Tracks`: 各列・行の開始位置とサイズ、gap）を追加。キャンバスが境界を掴むために必要だが、座標の単一の真実を保つため engine が arrange で記録したものを渡す方式にした。

構造編集は `DocumentEdits`（画面なしでテスト可能）に分離:

| 操作 | 内容 |
|---|---|
| Wrap in a container | ノードを 1x1 コンテナで包む。配置・span・z を引き継ぐ |
| Split into a row / column | 包んだうえで空セルを隣に追加 |
| Merge with the next cell | span を広げる。**中身のあるセルは吸収せず拒否**（黙って消さない） |
| Dissolve this container | 子を親グリッドへ持ち上げ。root は子が 1 つのときのみ |
| Convert to overlay | Stack 化して z を 0..n に振り直す |
| Bring forward / Send backward | 同一親内の z のみ |
| Delete | ノード削除。**元画像には触れない** |
| setBoundary | 隣接 2 トラックを fraction 化。両側に最低 5% を残す |

キャンバスのジェスチャ: 境界ドラッグ（カーソル変化つき）、セル間ドラッグで移動・入れ替え（ドロップ先をハイライト）、Ctrl+ホイールでズーム、Shift+ドラッグでパン、右クリックメニュー、Delete キー。

### GUI 検証（`PosterUiValidation`、18 項目すべて通過）

| 確認内容 | 結果 |
|---|---|
| 境界ドラッグの着地精度 | 期待 446.4 mm に対し実測 446.4 mm（誤差 0.01 mm 未満） |
| 占有セルへの移動 | 2 つが入れ替わる |
| Delete | パネルが 1 つ減り、元 TIFF の SHA-256 は不変 |

副次的に、過剰制約の警告が実運用で働くことも確認できた。列を 80% に広げるとアスペクト固定の行が A1 の高さを超え、ステータスバーに `Panels: the rows need 1016 mm but only 754 mm is available; reduce a track, lower the page size or drop an aspect ratio.` と出る。

### 未実装（S4 のうち）

- 端ハンドルによる Fixed 化リサイズ（グリッド優先の方針では境界ドラッグで代替できるため後回し）
- Style の drag & drop 適用（S7 待ち）
- 複数選択・マーキー選択

## 2026-09-10 (6): テキスト（S6）

130 tests, 0 failures, 0 errors。

### 非矩形テキスト領域

領域は「グリッドセルの和 − 除外」なので常に矩形の集合になる。汎用の `Area` ではなく矩形集合として扱い、行ごとの計算を厳密にした。連結判定は幾何ではなく**グリッド上の隣接**で行う（ガターで離れていても 1 つの領域として読むべきため）。

矩形と L 字を同じ経路に統一した。どちらも「shape のリスト」で、各行が「この高さで空いている水平 run」を問い合わせる。run が 2 つ返れば 1 行を 2 回埋めるので、ガターをまたいでテキストが continue する。

**run はバンド全体を含む矩形のみを数える。** 単なる重なりで判定すると、行境界にまたがる行が下の行の幅を借りて領域の外へはみ出す（実際に発生し、テストで固定した）。

領域を持つテキストノードは、自分が置かれたセルではなく**指定したセル群**を占有する（選択・ヒットテストが L 字全体を覆う）。

### Overflow

| ポリシー | 動作 |
|---|---|
| Warn（既定） | 不足量を報告するだけ。勝手に縮めると著者が決めた級数が崩れるため |
| Auto shrink | 70% まで段階的に縮小し、それでも入らなければ報告に戻る |
| Clip | 端で打ち切る |

Overflow はテキストを実測するまで分からないので、レンダラが収集してウィンドウのステータスバーが名前付きで報告する。

### PPTX への書き出し（プラン §7.5）

PowerPoint に非矩形テキストフレームは無いため、**正本保存では領域の矩形ごとに 1 つの編集可能テキストボックス**へ分割する。領域自体は project part に無損失で残るので情報は失われない。変わるのは分割位置が保存時に固定され、PowerPoint 内では各ボックス内でのみ再フローする点。

行が消費した文字範囲を記録するようにしたので分割は厳密で、`RegionTextExportTest` が**文字の欠落も重複も無い**ことを検証している。パートは `nodeId#partN` と命名し、逆取り込みの対象から外す（複数ボックスのテキストを 1 つの段落に戻す一意な方法が無いため）。

### UI

多重選択なしで領域を作れるよう、右クリックに「Extend the text area to the right / downwards」「Back to a single cell」を追加。1 セルずつ伸ばすので結果がその場で見える。

### 実データでの確認

`TextFlowValidation`: 図を右上に置き、本文が左列を流れて下段で全幅に広がることを目視確認（`artifacts/text-flow/l-shaped-flow.png`）。下段ではガターを挟んで「…comparable between / fields, and no channel…」と読み継がれている。

### 未実装

- Export ダイアログでの書き出し方式選択（行単位テキストボックス／ラスタ化）。現状は矩形分割のみ
- 離れた島の Manual 順序をキャンバス上の ①②③ で編集する UI（モデルとテストはあり）
- flow region がトラックの高さを駆動しない（領域はグリッドが与えた高さを使い、不足は overflow として報告する）

## 2026-09-10 (7): Token と Style（S7）

138 tests, 0 failures, 0 errors。

### 解決の順序

`StyleResolver` が「Node override → Node の style → 祖先からの継承 → 既定値」の順で解決する。Token は style と値の間に入る（style がトークン名を持ち、トークンが数値や色を持つ）。**style が別の style を参照せず、token の別名は 1 段のみ**という規則により、グラフは構造的に非循環で循環検出が要らない。

継承するのはタイポグラフィのみ（フォントサイズ・和欧フォント・文字色・太字等）。fill や border は設定したノードのものであって、セクションの背景色が全子孫を塗ってしまうことはない。

計画 §8.2 の狙い通り、「本文と Figure label でサイズは共通・欧文フォントは別」が Token 経由で表現でき、トークンの値を変えると両方が動くことをテストで固定した。

### 描画と PPTX の両方に接続

`NodeRenderer` と `PptxShapeBuilder` の両方が同じ resolver を使う。**画面で見えるスタイルがそのまま PowerPoint に届く**ことを検証（トークン由来の 28 pt が `sz="2800"`、style の Arial が `<a:latin>`、トークンの Yu Gothic が `<a:ea>`、ノード override の赤が `FF0000` として出力される）。

テキストの run は、run 自身に指定が無ければ解決済みスタイルを既定値として使う。

### UI

左ペインに Style Manager（一覧・New・Apply・Clear）。**一覧からキャンバスへドラッグ＆ドロップ**すると、落とした位置のノードにスタイルが適用される。GUI 検証でトークン 40 pt + 赤のスタイルを作ってタイトルに適用し、描画が変わることを確認した（`artifacts/poster-ui/08-styles.png`）。

### 未実装

- Inspector の各プロパティ行での「トークン参照 ⇄ 直接値」切り替え UI（計画 §8.4）。現状はスタイル単位の適用のみ
- トークン一覧の編集 UI（モデルと解決は完成）

## 2026-09-10 (8): PowerPoint 側編集の差分と取り込み（S9）

142 tests, 0 failures, 0 errors。

**project part が正本**という原則は崩していない。`PptxDiff` は shape tree から Document を再構築せず、差分の報告と、一意に戻せるものだけの取り込みを行う。

| 種別 | 扱い |
|---|---|
| テキスト | 取り込む。段落の runs は「最初の run の書式を保った 1 run」に置き換える（PowerPoint が run を結合・分割している可能性があり、どの override がどこに属していたかの推測は文面を壊すため） |
| 回転 | 報告のみ。ただし本ツール自身が書いた直角回転（行ラベル）は編集とみなさない |
| 反転 | 報告のみ |
| 非矩形テキストの分割ボックス（`#part`） | 報告のみ。複数ボックスを 1 段落に戻す一意な方法が無い |
| プロジェクトに無い shape | 報告のみ |

報告文はプラン §16 の書式に従う（`N compatible change(s) imported` / `N unsupported change(s) ignored` / `Unsupported:` の内訳）。再 open 時にダイアログで提示し、**取り込みは確認を取ってから**行う。

### 検証

`PptxDiffTest` が、保存済みファイルの slide part を PowerPoint がしそうな形に書き換えて確認:

- 無編集 → 報告ゼロ
- 本文書き換え → 1 件を importable と報告し、取り込み後に文面が変わり **bold が保たれる**
- `rot="780000"` → 「rotation 13 degrees」を unsupported として報告し、取り込まない
- `flipH="1"` → 「flipped」を unsupported として報告

### 未実装

- 色・塗り・枠・フォントの取り込み（検出の枠組みはあり、現状はテキストのみ）
- 幾何の条件付き取り込み（両辺が Fixed のときのみ提案する、というプラン §16 の項目）

## 2026-09-10 (9): Export ダイアログと TIFF（S10 の一部）

142 tests, 0 failures, 0 errors。GUI 検証 20 項目すべて通過。

`Export...` を専用ダイアログに置き換えた。形式（PNG / TIFF）、解像度、背景を選ぶと、**px サイズとメガピクセル数、および各画像の実効 dpi 一覧が書き出す前に表示される**。解像度が本質的な判断なので、決める前にコストと不足が見えるようにした。

TIFF は ImageJ の `FileSaver` 経由で物理サイズ（mm）を calibration に記録して書く。ただし**一括メモリ**が必要なため 120 メガピクセルの上限を設け、超える場合は「PNG は帯分割なので上限が無い」と案内して拒否する（黙って OutOfMemory で落とすより正直）。

### 未実装（S10 の残り）

- **PDF**。ベクタ + フォント埋め込みには PDFBox 等の追加依存が必要で、JAR が数 MB 増える。計画通りここで判断が要る（追加する / PPTX 経由に留める）
- 印刷設定（bleed・trim mark）
- TIFF のストリーミング書き出し
