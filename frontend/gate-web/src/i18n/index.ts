import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import ko from './locales/ko.json';
import en from './locales/en.json';

const LANG_KEY = 'lang';
const savedLang = localStorage.getItem(LANG_KEY) ?? 'ko';

i18n
  .use(initReactI18next)
  .init({
    resources: {
      ko: { translation: ko },
      en: { translation: en },
    },
    lng:          savedLang,
    fallbackLng:  'ko',
    interpolation: { escapeValue: false },
  });

/** 언어 변경 + localStorage 저장 */
export function changeLanguage(lang: 'ko' | 'en') {
  localStorage.setItem(LANG_KEY, lang);
  i18n.changeLanguage(lang);
}

export default i18n;
