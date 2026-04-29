# screentrans

[English](README.md) | [简体中文](README.zh_CN.md)

**This project is in its early stages. Some features, error handling, documentation, and usage instructions are not yet complete.**

**During the development of this project, AI-assisted programming techniques were used. Although every effort has been made to ensure the correctness and stability of the code, there is no guarantee that it is entirely free of errors or defects. In the event that any bugs in this project lead to abnormal calls to the API, misuse, data breaches, service interruptions, or any other direct or indirect losses or consequences, I (and the project contributors) shall not be held liable. You, as the user of this project and its associated APIs, should assess the risks on your own and bear all potential consequences arising from such use.**

An open-source Android OCR Screen Translator with customizable LLM API integration.

You can also ignore some recognized text based on the size of the text area and the regular expression of the text content.

The built-in OCR model can only recognize Simplified Chinese, Traditional Chinese, English, and Japanese. However, you can also load custom OCR models to support other languages.


## How to Use

By default, DeepSeek API is selected. Simply enter your API Key in the settings to start translating.

In Region Selection Mode:

* Single-click the floating ball to start rectangular selection translation.

* Double-click for a more convenient vertical-range selection.


## Screenshots

| Main Activity | Region Select | Translated 1 | Translated 2 |
| :---: | :---: | :---: | :---: |
| <img src="images/demo_main_activity.jpg" width="200"> | <img src="images/demo_region_select.jpg" width="200"> | <img src="images/demo_region_select_translated_1.jpg" width="200"> | <img src="images/demo_region_select_translated_2.jpg" width="200"> |

Required Permissions:

* Floating Window Permission: Necessary for covering the original text.

* Screen Recording Permission: Used to capture screenshots for OCR recognition.

Recommended Permissions:

* Notification Permission: Enables stable background Toast notifications once granted.

* Clipboard Write Permission: On some customized ROMs, needs to be set to "Always allow".

* Background Pop-up Permission: Since the system automatically revokes screen recording permission when the screen is off, enabling this allows you to conveniently re-request screen recording permission.
