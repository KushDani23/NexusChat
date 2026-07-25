/**
 * theme-manager.js
 * Manages dark / light theme switching, persistence in localStorage,
 * and initial system-preference detection.
 */
const ThemeManager = (() => {
    const STORAGE_KEY = 'chatAppTheme';
    const DARK  = 'dark';
    const LIGHT = 'light';

    let currentTheme = LIGHT;

    /** Detect the OS/browser colour-scheme preference. */
    function getSystemPreference() {
        if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            return DARK;
        }
        return LIGHT;
    }

    /** Apply the given theme to <html data-theme="…"> and update the toggle UI. */
    function applyTheme(theme) {
        currentTheme = theme;
        document.documentElement.setAttribute('data-theme', theme);

        const btn    = document.getElementById('themeToggle');
        const sunEl  = document.querySelector('.sun-icon');
        const moonEl = document.querySelector('.moon-icon');

        if (btn) {
            btn.setAttribute('aria-label', theme === DARK ? 'Switch to light mode' : 'Switch to dark mode');
        }
        if (sunEl)  sunEl.style.opacity  = theme === DARK  ? '0.4' : '1';
        if (moonEl) moonEl.style.opacity = theme === LIGHT ? '0.4' : '1';

        // Persist
        try { localStorage.setItem(STORAGE_KEY, theme); } catch (_) {}
    }

    /** Toggle between dark and light. */
    function toggleTheme() {
        applyTheme(currentTheme === DARK ? LIGHT : DARK);
    }

    /** Return the currently active theme string. */
    function getCurrentTheme() { return currentTheme; }

    /**
     * Initialise: load saved preference or fall back to system preference.
     * Call once on DOMContentLoaded.
     */
    function init() {
        let saved = null;
        try { saved = localStorage.getItem(STORAGE_KEY); } catch (_) {}
        const theme = (saved === DARK || saved === LIGHT) ? saved : getSystemPreference();
        applyTheme(theme);
    }

    return { init, toggleTheme, applyTheme, getCurrentTheme, getSystemPreference };
})();
