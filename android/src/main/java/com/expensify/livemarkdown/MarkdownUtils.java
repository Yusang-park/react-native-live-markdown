package com.expensify.livemarkdown;

import static com.facebook.infer.annotation.ThreadConfined.UI;

import android.content.res.AssetManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import androidx.annotation.NonNull;

import com.facebook.infer.annotation.ThreadConfined;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.views.text.CustomLineHeightSpan;
import com.facebook.soloader.SoLoader;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class MarkdownUtils {
  static {
    SoLoader.loadLibrary("livemarkdown");
  }

  private static boolean IS_RUNTIME_INITIALIZED = false;

  @ThreadConfined(UI)
  public static void maybeInitializeRuntime(AssetManager assetManager) {
    UiThreadUtil.assertOnUiThread();
    if (IS_RUNTIME_INITIALIZED) {
      return;
    }
    try {
      InputStream inputStream = assetManager.open("react-native-live-markdown-parser.js");
      byte[] buffer = new byte[inputStream.available()];
      inputStream.read(buffer);
      inputStream.close();
      String code = new String(buffer);
      nativeInitializeRuntime(code);
      IS_RUNTIME_INITIALIZED = true;
    } catch (IOException e) {
      throw new RuntimeException("Failed to initialize Markdown runtime");
    }
  }

  private static native void nativeInitializeRuntime(String code);

  @ThreadConfined(UI)
  private static String parseMarkdown(String input) {
    UiThreadUtil.assertOnUiThread();
    return nativeParseMarkdown(input);
  }

  private static native String nativeParseMarkdown(String input);

  private String mPrevInput;

  private String mPrevOutput;

  private MarkdownStyle mMarkdownStyle;

  public void setMarkdownStyle(@NonNull MarkdownStyle markdownStyle) {
    mMarkdownStyle = markdownStyle;
  }

  public void applyMarkdownFormatting(SpannableStringBuilder ssb) {
    Objects.requireNonNull(mMarkdownStyle, "mMarkdownStyle is null");

    removeSpans(ssb);

    String input = ssb.toString();
    String output;
    if (input.equals(mPrevInput)) {
      output = mPrevOutput;
    } else {
      output = parseMarkdown(input);
      mPrevInput = input;
      mPrevOutput = output;
    }

    try {
      JSONArray ranges = new JSONArray(output);
      for (int i = 0; i < ranges.length(); i++) {
        JSONArray range = ranges.getJSONArray(i);
        String type = range.getString(0);
        int start = range.getInt(1);
        int end = start + range.getInt(2);
        applyRange(ssb, type, start, end);
      }
    } catch (JSONException e) {
      // Do nothing
    }
  }

  private void applyRange(SpannableStringBuilder ssb, String type, int start, int end) {
    switch (type) {
      case "bold":
        setSpan(ssb, new MarkdownBoldSpan(), start, end);
        break;
      case "italic":
        setSpan(ssb, new MarkdownItalicSpan(), start, end);
        break;
      case "strikethrough":
        setSpan(ssb, new MarkdownStrikethroughSpan(), start, end);
        break;
      case "mention-here":
        setSpan(ssb, new MarkdownBoldSpan(), start, end);
        setSpan(ssb, new MarkdownBackgroundColorSpan(mMarkdownStyle.getMentionHereBackgroundColor()), start, end);
        break;
      case "mention-user":
        setSpan(ssb, new MarkdownBoldSpan(), start, end);
        // TODO: change mention color when it mentions current user
        setSpan(ssb, new MarkdownBackgroundColorSpan(mMarkdownStyle.getMentionUserBackgroundColor()), start, end);
        break;
      case "syntax":
        setSpan(ssb, new MarkdownBoldSpan(), start, end);
        setSpan(ssb, new MarkdownForegroundColorSpan(mMarkdownStyle.getSyntaxColor()), start, end);
        break;
      case "link":
        setSpan(ssb, new MarkdownUnderlineSpan(), start, end);
        setSpan(ssb, new MarkdownForegroundColorSpan(mMarkdownStyle.getLinkColor()), start, end);
        break;
      case "code":
        setSpan(ssb, new MarkdownFontFamilySpan(mMarkdownStyle.getCodeFontFamily()), start, end);
        setSpan(ssb, new MarkdownForegroundColorSpan(mMarkdownStyle.getCodeColor()), start, end);
        setSpan(ssb, new MarkdownBackgroundColorSpan(mMarkdownStyle.getCodeBackgroundColor()), start, end);
        break;
      case "pre":
        setSpan(ssb, new MarkdownFontFamilySpan(mMarkdownStyle.getPreFontFamily()), start, end);
        setSpan(ssb, new MarkdownForegroundColorSpan(mMarkdownStyle.getPreColor()), start, end);
        setSpan(ssb, new MarkdownBackgroundColorSpan(mMarkdownStyle.getPreBackgroundColor()), start, end);
        break;
      case "h1":
        setSpan(ssb, new MarkdownBoldSpan(), start, end);
        CustomLineHeightSpan[] spans = ssb.getSpans(0, ssb.length(), CustomLineHeightSpan.class);
        if (spans.length >= 1) {
          int lineHeight = spans[0].getLineHeight();
          setSpan(ssb, new MarkdownLineHeightSpan(lineHeight * 1.5f), start, end);
        }
        // NOTE: size span must be set after line height span to avoid height jumps
        setSpan(ssb, new MarkdownFontSizeSpan(mMarkdownStyle.getH1FontSize()), start, end);
        break;
      case "blockquote":
        MarkdownBlockquoteSpan span = new MarkdownBlockquoteSpan(
          mMarkdownStyle.getBlockquoteBorderColor(),
          mMarkdownStyle.getBlockquoteBorderWidth(),
          mMarkdownStyle.getBlockquoteMarginLeft(),
          mMarkdownStyle.getBlockquotePaddingLeft());
        setSpan(ssb, span, start, end);
        break;
      default:
        throw new IllegalStateException("Unsupported type: " + type);
    }
  }

  private void setSpan(SpannableStringBuilder ssb, MarkdownSpan span, int start, int end) {
    ssb.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  }

  private void removeSpans(SpannableStringBuilder ssb) {
    // We shouldn't use `removeSpans()` because it also removes SpellcheckSpan, SuggestionSpan etc.
    MarkdownSpan[] spans = ssb.getSpans(0, ssb.length(), MarkdownSpan.class);
    for (MarkdownSpan span : spans) {
      ssb.removeSpan(span);
    }
  }
}