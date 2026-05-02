# ScreenTrans

[简体中文](README.md) | [English](README.en_US.md)

[![Android Build](https://github.com/longipinnatus/screentrans/actions/workflows/android-build.yml/badge.svg)](https://github.com/longipinnatus/screentrans/actions/workflows/android-build.yml)

An open-source Android OCR Screen Translator with customizable LLM API integration.

> [!IMPORTANT]  
> **This project is in its early stages. Some features, error handling, documentation, and usage instructions are not yet complete.**
> 
> **During the development of this project, AI-assisted programming techniques were used. Although every effort has been made to ensure the correctness and stability of the code, there is no guarantee that it is entirely free of errors or defects. In the event that any bugs in this project lead to abnormal calls to the API, misuse, data breaches, service interruptions, or any other direct or indirect losses or consequences, I (and the project contributors) shall not be held liable. You, as the user of this project and its associated APIs, should assess the risks on your own and bear all potential consequences arising from such use.**


## Download

You can download the latest version from [GitHub Releases](https://github.com/longipinnatus/screentrans/releases/latest).

For most Android devices, downloading `app-arm64-v8a-release.apk` is sufficient.


## Project Features

### 1. Fast and Lightweight OCR Recognition
---
* **Multimodel Support**: Built-in lightweight OCR models (supporting Simplified/Traditional Chinese, English, and Japanese), with the ability to load custom ONNX models for higher precision or specific language adaptation.
* **Model Parameter Tuning**: Provides a parameter adjustment panel to optimize recognition parameters for source files with varying clarity, improving recognition rates in specific scenarios.
* **Vertical Text Adaptation**: Supports vertical text recognition for Chinese and Japanese, catering to reading scenarios such as manga, novels, and classical texts.
* **Horizontal/Vertical Scene Adaptation**: Supports seamless switching between landscape and portrait modes on devices.

### 2. Flexible LLM Integration
---
* **Standard API Access**: Supports the OpenAI API protocol and compatible custom endpoints.
* **Streaming Output**: Enables scrolling return of translated content, eliminating the need to wait for full responses when processing multiple text boxes, thus reducing wait time.
* **Customizable Translation Style**: Supports user-defined prompts, allowing adjustments to translation style and terminology based on the context.
* **Transparent Usage Metrics**: Built-in token usage statistics and cost calculation, providing real-time visibility into API consumption costs.

### 3. Automated Workflows
---
* **Customizable Exclusion Logic**: Supports automatic ignoring of irrelevant areas based on text box size and regular expressions, effectively filtering out page numbers, watermarks, etc.
* **Clipboard Synchronization**: Recognized results can be automatically copied, with flexible configuration of copied content (original text only, translation only, or side-by-side comparison).

### 4. Personalization
---
* **Font Flexibility**: Supports custom display fonts, compatible with external TTF/OTF font file imports.
* **UI Transparency Adjustment**: Transparency of both text boxes and the floating button can be set independently.
* **Dynamic Interaction Logic**: Supports automatic hiding of translated results after a countdown, or switching to manual dismissal mode to maintain a clean interface.
* **Color Adaptation**: Supports background color overlay, allowing translated text boxes to automatically blend with the original background, providing a near-native visual experience.


## How to Use

The DeepSeek API parameters are already set by default. You just need to enter your API Key in the settings to start translating.

Region selection mode (default):

* Click the floating button to select an area for translation;

* Double-click to enable a more convenient vertical range selection.

Full-screen translation mode: Click to recognize and translate the entire screen (I rarely use this feature, so the results may not be optimal).


## Screenshots

Game: IDOLY PRIDE (アイプラ), filtering of certain texts has been enabled.

| Main Activity | Region Select | Translated 1 | Translated 2 |
| :---: | :---: | :---: | :---: |
| <img src="images/demo_main_activity.jpg" width="200"> | <img src="images/demo_region_select.jpg" width="200"> | <img src="images/demo_region_select_translated_1.jpg" width="200"> | <img src="images/demo_region_select_translated_2.jpg" width="200"> |


## Permission Requirements

Required Permissions:

* Floating Window Permission: Necessary for covering the original text. (If unable to open, you may need to click "Remove All Permission Restrictions")

* Screen Recording Permission: Used to capture screenshots for OCR recognition.

Recommended Permissions:

* Notification Permission: Enables stable background Toast notifications once granted.

* Clipboard Write Permission: On some customized ROMs, needs to be set to "Always allow".

* Background Pop-up Permission: Since the system automatically revokes screen recording permission when the screen is off, enabling this allows you to conveniently re-request screen recording permission.

| Permission Settings | Special Permission Settings |
| :---: | :---: |
| <img src="images/permission_config.jpg" width="300"> | <img src="images/permission_special.jpg" width="300"> |
