// Shared card typography — single source of truth for both Face Auth and Vein pages

export const CARD_TITLE_SIZE = "14px";
export const CARD_TITLE_WEIGHT = "600";
export const CARD_TITLE_COLOR = "#334155";
export const CARD_TITLE_TRACKING = "-0.35px";

export const CARD_SUBTITLE_SIZE = "13px";
export const CARD_SUBTITLE_WEIGHT = "400";
export const CARD_SUBTITLE_COLOR = "#64748B";
export const CARD_SUBTITLE_TRACKING = "-0.325px";

export const CARD_LINE_HEIGHT = "1.4";

// Tailwind class strings — use these in HTML/JSX elements
export const cardTitleClass =
  "text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]";

export const cardSubtitleClass =
  "text-[13px] text-[#64748b] tracking-[-0.325px] leading-[1.4]";

// Inline style props — use these in SVG <text> elements (Face Auth page)
export const svgCardTitleProps = {
  fontSize: CARD_TITLE_SIZE,
  fontFamily:
    "Pretendard,-apple-system,BlinkMacSystemFont,system-ui,sans-serif",
  fontWeight: CARD_TITLE_WEIGHT,
  fill: CARD_TITLE_COLOR,
  letterSpacing: "-0.35",
};

export const svgCardSubtitleProps = {
  fontSize: CARD_SUBTITLE_SIZE,
  fontFamily:
    "Pretendard,-apple-system,BlinkMacSystemFont,system-ui,sans-serif",
  fontWeight: CARD_SUBTITLE_WEIGHT,
  fill: CARD_SUBTITLE_COLOR,
  letterSpacing: "-0.325",
};
