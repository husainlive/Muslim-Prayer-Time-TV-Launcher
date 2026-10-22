/**
 * Smart TV Launcher Application Engine
 * High-Density 2K/4K Native Aurora Background Renderer, Glassmorphic 10-Foot UI,
 * Dynamic Prayer & Iqama Countdown State Machine, and TV Remote Hardware Listeners.
 */

(function () {
    'use strict';

    // =========================================================================
    // 1. CONFIGURATION & STATE MANAGEMENT
    // =========================================================================
    const STATE = {
        timeFormat24: false,
        hijriOffset: 0,
        currentLocationIndex: 0,
        locations: [
            { name: "Doha, Qatar", lat: 25.2854, lon: 51.5310, tz: 3 },
            { name: "Riyadh, KSA", lat: 24.7136, lon: 46.6753, tz: 3 },
            { name: "Mecca, KSA", lat: 21.3891, lon: 39.8579, tz: 3 },
            { name: "Dubai, UAE", lat: 25.2048, lon: 55.2708, tz: 4 },
            { name: "Cairo, Egypt", lat: 30.0444, lon: 31.2357, tz: 2 },
            { name: "Istanbul, Turkey", lat: 41.0082, lon: 28.9784, tz: 3 },
            { name: "London, UK", lat: 51.5074, lon: -0.1278, tz: 1 }
        ],
        methods: ["Umm Al-Qura", "Muslim World League", "Egyptian General", "ISNA (North America)"],
        currentMethodIndex: 0,
        azanSounds: ["Azan 1 (Makkah)", "Azan 2 (Madinah)", "Azan 3 (Al-Aqsa)", "Soft Chime", "Mute"],
        currentAzanIndex: 0,
        
        // Exact Earlier TV Background: 2K / 4K Pixel Density & Aurora Configuration
        bgDensityModes: ["4K Ultra-HD (Native)", "2K Quad-HD", "Full-HD (1080p)"],
        currentBgDensityIndex: 0,
        auroraIntensities: [
            { label: "100% (Balanced)", mult: 1.0 },
            { label: "125% (Vibrant)", mult: 1.25 },
            { label: "80% (Subtle)", mult: 0.8 }
        ],
        currentAuroraIntensityIndex: 0,

        showPrayerBar: true,
        showWeather: true,
        tempUnitCelsius: true,
        glassEffectsOn: true,
        screensaverEnabled: true,
        screensaverTimeoutMinutes: 5,
        screensaverThemes: ["Built-in Drift Clock", "Aerial Views (Dream)", "Cosmic Starfield 4K", "Minimalist OLED Clock", "YouTube TV (App)"],
        currentScreensaverThemeIndex: 0,
        lastActivityTime: Date.now(),
        isScreensaverActive: false,
        testIqamaSimulation: null, // Holds simulated Iqama target if testing
        activeModal: null // 'inputs', 'youtube', 'settings', or null
    };

    // Fixed Iqama Offsets strictly matching user specification:
    // Fajr +25m, Dhuhr +20m, Asr +25m, Maghrib +10m, Isha +20m
    const IQAMA_OFFSETS_MINUTES = {
        Fajr: 25,
        Dhuhr: 20,
        Asr: 25,
        Maghrib: 10,
        Isha: 20
    };

    // Standard baseline prayer schedules (adjusted by location)
    const BASELINE_PRAYER_SCHEDULES = {
        "Doha, Qatar": { Fajr: "04:18", Sunrise: "05:35", Dhuhr: "11:19", Asr: "14:36", Maghrib: "17:02", Isha: "18:32" },
        "Riyadh, KSA": { Fajr: "04:32", Sunrise: "05:49", Dhuhr: "11:34", Asr: "14:52", Maghrib: "17:19", Isha: "18:49" },
        "Mecca, KSA": { Fajr: "04:55", Sunrise: "06:11", Dhuhr: "12:00", Asr: "15:20", Maghrib: "17:48", Isha: "19:18" },
        "Dubai, UAE": { Fajr: "04:54", Sunrise: "06:12", Dhuhr: "12:08", Asr: "15:27", Maghrib: "17:58", Isha: "19:15" },
        "Cairo, Egypt": { Fajr: "04:30", Sunrise: "05:54", Dhuhr: "11:40", Asr: "14:50", Maghrib: "17:18", Isha: "18:36" },
        "Istanbul, Turkey": { Fajr: "05:35", Sunrise: "07:05", Dhuhr: "12:50", Asr: "15:45", Maghrib: "18:22", Isha: "19:45" },
        "London, UK": { Fajr: "05:40", Sunrise: "07:35", Dhuhr: "12:55", Asr: "15:30", Maghrib: "18:02", Isha: "19:42" }
    };

    // Weather condition profiles
    const WEATHER_PROFILES = [
        { tempC: 28, condition: "Clear", icon: "sun" },
        { tempC: 26, condition: "Partly Cloudy", icon: "cloud-sun" },
        { tempC: 31, condition: "Sunny", icon: "sun" },
        { tempC: 24, condition: "Breezy", icon: "wind" },
        { tempC: 22, condition: "Mild Rain", icon: "rain" }
    ];
    let currentWeatherIndex = 0;

    // =========================================================================
    // 2. HIGH-DENSITY 2K / 4K EARLIER TV AURORA BACKGROUND RENDERER
    // =========================================================================
    /**
     * Preserves the EXACT iconic earlier TV launcher background:
     * - Deep midnight slate-blue base (#04060e to #070a14)
     * - Upper glowing Cyan/Teal horizontal Aurora beam across the left & center
     * - Upper glowing Purple/Violet horizontal Aurora beam across the right
     * - Mid-left subtle oceanic blue underglow
     * - Ultra-high native pixel density rendering (up to 3840x2160 native pixels)
     * - Zero-banding high-frequency micro-dither to eliminate 8-bit/10-bit color stepping on 4K TVs
     */
    class HighDensityAuroraBackground {
        constructor(canvasId) {
            this.canvas = document.getElementById(canvasId);
            if (!this.canvas) return;
            this.ctx = this.canvas.getContext('2d', { alpha: false });
            this.width = 0;
            this.height = 0;
            this.dpr = 1;
            this.time = 0;
            this.ditherPattern = null;

            this.init();
        }

        init() {
            this.createDitherPattern();
            this.resize();
            window.addEventListener('resize', () => this.resize());

            this.animate = this.animate.bind(this);
            requestAnimationFrame(this.animate);
        }

        createDitherPattern() {
            // Generate seamless 256x256 sub-pixel anti-banding micro-noise pattern
            const pCanvas = document.createElement('canvas');
            pCanvas.width = 256;
            pCanvas.height = 256;
            const pCtx = pCanvas.getContext('2d');
            const imgData = pCtx.createImageData(256, 256);
            const data = imgData.data;

            for (let i = 0; i < data.length; i += 4) {
                const val = Math.floor(Math.random() * 255);
                data[i] = val;
                data[i + 1] = val;
                data[i + 2] = val;
                data[i + 3] = 14; // ~5% subtle dithering to break quantization steps
            }

            pCtx.putImageData(imgData, 0, 0);
            this.ditherPattern = this.ctx.createPattern(pCanvas, 'repeat');
        }

        resize() {
            // Calculate effective DPR based on user density mode
            const baseDpr = window.devicePixelRatio || 1;
            let targetDpr = baseDpr;

            if (STATE.currentBgDensityIndex === 0) {
                // 4K Ultra-HD Native: Full DPR scaling up to 2.5x
                targetDpr = Math.max(1.5, Math.min(baseDpr, 2.5));
            } else if (STATE.currentBgDensityIndex === 1) {
                // 2K Quad-HD
                targetDpr = Math.max(1.25, Math.min(baseDpr, 1.75));
            } else {
                // FHD 1080p
                targetDpr = 1.0;
            }

            this.dpr = targetDpr;
            this.width = window.innerWidth;
            this.height = window.innerHeight;

            this.canvas.width = Math.round(this.width * this.dpr);
            this.canvas.height = Math.round(this.height * this.dpr);
            this.canvas.style.width = `${this.width}px`;
            this.canvas.style.height = `${this.height}px`;

            this.ctx.setTransform(1, 0, 0, 1, 0, 0);
            this.ctx.scale(this.dpr, this.dpr);
        }

        animate() {
            this.time += 0.003;
            const intensity = STATE.auroraIntensities[STATE.currentAuroraIntensityIndex].mult;

            const w = this.width;
            const h = this.height;

            // 1. Base Deep Midnight Fill (Matches earlier background #04060e)
            this.ctx.fillStyle = '#04060e';
            this.ctx.fillRect(0, 0, w, h);

            // 2. Vertical Atmospheric Base Gradient
            const skyGrad = this.ctx.createLinearGradient(0, 0, 0, h);
            skyGrad.addColorStop(0, '#081722');
            skyGrad.addColorStop(0.35, '#060f1b');
            skyGrad.addColorStop(0.65, '#040711');
            skyGrad.addColorStop(1, '#020409');
            this.ctx.fillStyle = skyGrad;
            this.ctx.fillRect(0, 0, w, h);

            // Subtle organic micro-wave breathing
            const breathOffset = Math.sin(this.time * 1.5) * 6;
            const breathScale = 1.0 + Math.sin(this.time * 2.0) * 0.03;

            // 3. Iconic Earlier TV Cyan/Teal Aurora Light Ribbon (Upper Left/Center)
            // Color profile: rgb(28, 167, 164) / #1ca7a4
            this.ctx.save();
            const cyanCenterX = w * 0.38 + Math.cos(this.time * 0.8) * 8;
            const cyanCenterY = h * 0.15 + breathOffset;
            const cyanRadiusX = w * 0.52 * breathScale;
            const cyanRadiusY = h * 0.17 * breathScale;

            this.ctx.translate(cyanCenterX, cyanCenterY);
            this.ctx.scale(1, cyanRadiusY / cyanRadiusX);

            const cyanGrad = this.ctx.createRadialGradient(0, 0, 0, 0, 0, cyanRadiusX);
            cyanGrad.addColorStop(0, `rgba(31, 172, 166, ${0.92 * intensity})`);
            cyanGrad.addColorStop(0.25, `rgba(22, 142, 142, ${0.72 * intensity})`);
            cyanGrad.addColorStop(0.55, `rgba(12, 85, 115, ${0.38 * intensity})`);
            cyanGrad.addColorStop(0.82, `rgba(8, 42, 68, ${0.12 * intensity})`);
            cyanGrad.addColorStop(1, 'transparent');

            this.ctx.fillStyle = cyanGrad;
            this.ctx.beginPath();
            this.ctx.arc(0, 0, cyanRadiusX, 0, Math.PI * 2);
            this.ctx.fill();
            this.ctx.restore();

            // 4. Iconic Earlier TV Purple/Violet Aurora Light Ribbon (Upper Right)
            // Color profile: rgb(70, 65, 159) / #46419f
            this.ctx.save();
            const violetCenterX = w * 0.74 + Math.sin(this.time * 0.9) * 10;
            const violetCenterY = h * 0.27 - (breathOffset * 0.7);
            const violetRadiusX = w * 0.44 * breathScale;
            const violetRadiusY = h * 0.19 * breathScale;

            this.ctx.translate(violetCenterX, violetCenterY);
            this.ctx.scale(1, violetRadiusY / violetRadiusX);

            const violetGrad = this.ctx.createRadialGradient(0, 0, 0, 0, 0, violetRadiusX);
            violetGrad.addColorStop(0, `rgba(88, 64, 168, ${0.88 * intensity})`);
            violetGrad.addColorStop(0.30, `rgba(68, 52, 142, ${0.60 * intensity})`);
            violetGrad.addColorStop(0.60, `rgba(38, 28, 85, ${0.28 * intensity})`);
            violetGrad.addColorStop(0.85, `rgba(18, 14, 45, ${0.08 * intensity})`);
            violetGrad.addColorStop(1, 'transparent');

            this.ctx.fillStyle = violetGrad;
            this.ctx.beginPath();
            this.ctx.arc(0, 0, violetRadiusX, 0, Math.PI * 2);
            this.ctx.fill();
            this.ctx.restore();

            // 5. Mid-Left Oceanic Deep Blue Atmosphere
            this.ctx.save();
            const blueGrad = this.ctx.createRadialGradient(w * 0.22, h * 0.33, 0, w * 0.22, h * 0.33, w * 0.35);
            blueGrad.addColorStop(0, `rgba(14, 46, 78, ${0.48 * intensity})`);
            blueGrad.addColorStop(0.65, `rgba(8, 24, 44, ${0.18 * intensity})`);
            blueGrad.addColorStop(1, 'transparent');
            this.ctx.fillStyle = blueGrad;
            this.ctx.fillRect(0, 0, w, h);
            this.ctx.restore();

            // 6. High-Density Micro-Dither Overlay Pass (Eliminates Banding on 2K/4K TV Screens)
            if (this.ditherPattern) {
                this.ctx.save();
                this.ctx.fillStyle = this.ditherPattern;
                this.ctx.globalAlpha = 0.65;
                this.ctx.fillRect(0, 0, w, h);
                this.ctx.restore();
            }

            requestAnimationFrame(this.animate);
        }
    }

    // =========================================================================
    // 3. SCREENSAVER ENGINE (4K Starfield & Glanceable OLED Clock)
    // =========================================================================
    class ScreensaverEngine {
        constructor() {
            this.overlay = document.getElementById('screensaver-overlay');
            this.canvas = document.getElementById('screensaver-canvas');
            this.clockEl = document.getElementById('screensaver-clock');
            this.metaEl = document.getElementById('screensaver-meta');
            if (!this.canvas) return;

            this.ctx = this.canvas.getContext('2d');
            this.stars = [];
            this.numStars = 200;
            this.animId = null;

            this.init();
        }

        init() {
            this.resize();
            window.addEventListener('resize', () => this.resize());

            // Build 3D warp star array
            for (let i = 0; i < this.numStars; i++) {
                this.stars.push({
                    x: (Math.random() - 0.5) * 2000,
                    y: (Math.random() - 0.5) * 2000,
                    z: Math.random() * 1000
                });
            }
        }

        resize() {
            const dpr = window.devicePixelRatio || 1;
            this.width = window.innerWidth;
            this.height = window.innerHeight;
            this.canvas.width = this.width * dpr;
            this.canvas.height = this.height * dpr;
            this.ctx.scale(dpr, dpr);
        }

        start() {
            STATE.isScreensaverActive = true;
            this.overlay.classList.add('active');
            this.overlay.setAttribute('aria-hidden', 'false');
            this.updateClock();

            const render = () => {
                this.ctx.fillStyle = 'rgba(3, 6, 12, 0.25)';
                this.ctx.fillRect(0, 0, this.width, this.height);

                const cx = this.width / 2;
                const cy = this.height / 2;

                this.stars.forEach(star => {
                    star.z -= 1.8;
                    if (star.z <= 0) {
                        star.z = 1000;
                        star.x = (Math.random() - 0.5) * 2000;
                        star.y = (Math.random() - 0.5) * 2000;
                    }

                    const k = 300 / star.z;
                    const px = star.x * k + cx;
                    const py = star.y * k + cy;

                    if (px >= 0 && px < this.width && py >= 0 && py < this.height) {
                        const size = Math.max(0.6, (1 - star.z / 1000) * 3);
                        const alpha = Math.min(1, (1 - star.z / 1000) * 1.5);
                        this.ctx.fillStyle = `rgba(186, 230, 253, ${alpha})`;
                        this.ctx.beginPath();
                        this.ctx.arc(px, py, size, 0, Math.PI * 2);
                        this.ctx.fill();
                    }
                });

                if (STATE.isScreensaverActive) {
                    this.animId = requestAnimationFrame(render);
                }
            };
            this.animId = requestAnimationFrame(render);
        }

        stop() {
            STATE.isScreensaverActive = false;
            this.overlay.classList.remove('active');
            this.overlay.setAttribute('aria-hidden', 'true');
            if (this.animId) cancelAnimationFrame(this.animId);
            STATE.lastActivityTime = Date.now();
        }

        updateClock() {
            const now = new Date();
            let hours = now.getHours();
            const minutes = String(now.getMinutes()).padStart(2, '0');
            const ampm = hours >= 12 ? 'PM' : 'AM';
            if (!STATE.timeFormat24) {
                hours = hours % 12 || 12;
            }
            if (this.clockEl) {
                this.clockEl.textContent = `${String(hours).padStart(2, '0')}:${minutes} ${STATE.timeFormat24 ? '' : ampm}`;
            }
            if (this.metaEl) {
                const greg = getFormattedGregorianDate();
                const hijri = getFormattedHijriDate(STATE.hijriOffset);
                this.metaEl.textContent = `${greg} • ${hijri}`;
            }
        }
    }

    // =========================================================================
    // 4. DATE, TIME, GREETING & WEATHER HELPERS
    // =========================================================================
    function getDynamicGreeting() {
        const hour = new Date().getHours();
        if (hour >= 5 && hour < 12) return "Good Morning";
        if (hour >= 12 && hour < 17) return "Good Afternoon";
        if (hour >= 17 && hour < 21) return "Good Evening";
        return "Good Night";
    }

    function getFormattedGregorianDate() {
        const options = { weekday: 'long', day: 'numeric', month: 'long' };
        return new Date().toLocaleDateString('en-US', options);
    }

    function getFormattedHijriDate(offsetDays = 0) {
        // Approximate calculation with moon sighting offset
        const baseEpoch = new Date(2026, 9, 21).getTime(); // Oct 21, 2026 ~ 10 Jumada I 1448
        const now = Date.now() + offsetDays * 86400000;
        const diffDays = Math.floor((now - baseEpoch) / 86400000);

        let day = 10 + diffDays;
        let month = "Jumada I";
        let year = 1448;

        if (day > 30) {
            day = day - 30;
            month = "Jumada II";
        } else if (day < 1) {
            day = 30 + day;
            month = "Rabi' II";
        }

        return `${day} ${month} ${year} AH`;
    }

    // Vector weather SVG generator
    function getWeatherSvg(iconType) {
        switch (iconType) {
            case "sun":
                return `<svg class="weather-vector-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="12" r="5"></circle>
                    <line x1="12" y1="1" x2="12" y2="3"></line>
                    <line x1="12" y1="21" x2="12" y2="23"></line>
                    <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line>
                    <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line>
                    <line x1="1" y1="12" x2="3" y2="12"></line>
                    <line x1="21" y1="12" x2="23" y2="12"></line>
                    <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line>
                    <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line>
                </svg>`;
            case "cloud-sun":
                return `<svg class="weather-vector-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M12 2v2"></path>
                    <path d="M4.93 4.93l1.41 1.41"></path>
                    <path d="M20 12h2"></path>
                    <path d="M17.5 7.5a6 6 0 0 0-8.5 8.5H6a4 4 0 0 0 0 8h11a5 5 0 0 0 5-5 5 5 0 0 0-4.5-5z"></path>
                </svg>`;
            case "rain":
                return `<svg class="weather-vector-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <line x1="16" y1="13" x2="16" y2="21"></line>
                    <line x1="8" y1="13" x2="8" y2="21"></line>
                    <line x1="12" y1="15" x2="12" y2="23"></line>
                    <path d="M20 16.58A5 5 0 0 0 18 7h-1.26A8 8 0 1 0 4 15.25"></path>
                </svg>`;
            default:
                return `<svg class="weather-vector-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="12" r="5"></circle>
                    <line x1="12" y1="1" x2="12" y2="3"></line>
                    <line x1="12" y1="21" x2="12" y2="23"></line>
                </svg>`;
        }
    }

    // =========================================================================
    // 5. DYNAMIC PRAYER & IQAMA COUNTDOWN STATE MACHINE
    // =========================================================================
    /**
     * Requirement:
     * - Same display location switch: Show time remaining until the next prayer in the header widget.
     * - Iqama Countdown Transition: As soon as a prayer time is reached, dynamically replace the
     *   "Next Prayer" counter in the exact same spot with an "Iqama Counter" counting down remaining minutes.
     * - Fixed offset logic relative to each prayer time:
     *   - Fajr: Fajr + 25m
     *   - Dhuhr: Dhuhr + 20m
     *   - Asr: Asr + 25m
     *   - Maghrib: Maghrib + 10m
     *   - Isha: Isha + 20m
     * - Once Iqama counter finishes, automatically revert display back to showing the counter for Next Prayer.
     */
    class PrayerCountdownEngine {
        constructor() {
            this.widget = document.getElementById('prayer-countdown-widget');
            this.modeBadge = document.getElementById('countdown-mode-label');
            this.targetName = document.getElementById('countdown-target-prayer');
            this.timerValue = document.getElementById('countdown-timer-value');
            this.subtext = document.getElementById('countdown-subtext');
            this.pulse = document.getElementById('countdown-pulse');
        }

        getPrayerTimesForToday() {
            const locName = STATE.locations[STATE.currentLocationIndex].name;
            const schedule = BASELINE_PRAYER_SCHEDULES[locName] || BASELINE_PRAYER_SCHEDULES["Doha, Qatar"];

            const now = new Date();
            const year = now.getFullYear();
            const month = now.getMonth();
            const day = now.getDate();

            const prayerList = [];
            const prayerNames = ["Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"];

            prayerNames.forEach(name => {
                const [h, m] = schedule[name].split(':').map(Number);
                const pDate = new Date(year, month, day, h, m, 0);
                prayerList.push({
                    name: name,
                    timeDate: pDate,
                    timeString: schedule[name],
                    iqamaOffset: IQAMA_OFFSETS_MINUTES[name] || 0
                });
            });

            return prayerList;
        }

        tick() {
            if (!this.widget) return;

            const now = new Date();
            const prayerList = this.getPrayerTimesForToday();

            // 1. Check for Active Test Simulation
            if (STATE.testIqamaSimulation) {
                const sim = STATE.testIqamaSimulation;
                const remainingSecs = Math.max(0, Math.floor((sim.endTime - Date.now()) / 1000));

                if (remainingSecs > 0) {
                    this.renderIqamaState(sim.prayerName, remainingSecs, sim.offsetMinutes);
                    return;
                } else {
                    // Test simulation complete -> revert to normal
                    STATE.testIqamaSimulation = null;
                    showToast("Iqama countdown completed. Reverted to Next Prayer.");
                }
            }

            // 2. Real-Time State Check: Are we currently inside an Iqama window?
            // An Iqama window is active if: now >= prayerTime and now < prayerTime + offset
            let activeIqamaPrayer = null;
            let activeIqamaRemainingSeconds = 0;

            for (const prayer of prayerList) {
                if (prayer.iqamaOffset > 0) {
                    const pStartMs = prayer.timeDate.getTime();
                    const iqamaEndMs = pStartMs + (prayer.iqamaOffset * 60 * 1000);
                    const nowMs = now.getTime();

                    if (nowMs >= pStartMs && nowMs < iqamaEndMs) {
                        activeIqamaPrayer = prayer;
                        activeIqamaRemainingSeconds = Math.floor((iqamaEndMs - nowMs) / 1000);
                        break;
                    }
                }
            }

            if (activeIqamaPrayer) {
                // Transition In-Place to Iqama Counter in the EXACT same spot
                this.renderIqamaState(
                    activeIqamaPrayer.name,
                    activeIqamaRemainingSeconds,
                    activeIqamaPrayer.iqamaOffset
                );
                this.updatePrayerCards(prayerList, activeIqamaPrayer.name, true);
                return;
            }

            // 3. Normal State: Find Next Upcoming Prayer
            let nextPrayer = null;
            let timeUntilNextSeconds = 0;

            for (const prayer of prayerList) {
                const diffSec = Math.floor((prayer.timeDate.getTime() - now.getTime()) / 1000);
                if (diffSec > 0) {
                    nextPrayer = prayer;
                    timeUntilNextSeconds = diffSec;
                    break;
                }
            }

            // If all prayers today passed, next prayer is Fajr tomorrow
            if (!nextPrayer) {
                const tomorrowFajr = new Date(prayerList[0].timeDate.getTime() + 86400000);
                nextPrayer = {
                    name: "Fajr",
                    timeDate: tomorrowFajr,
                    timeString: prayerList[0].timeString,
                    iqamaOffset: 25
                };
                timeUntilNextSeconds = Math.floor((tomorrowFajr.getTime() - now.getTime()) / 1000);
            }

            // Transition In-Place to Next Prayer Counter
            this.renderNextPrayerState(nextPrayer, timeUntilNextSeconds);
            this.updatePrayerCards(prayerList, nextPrayer.name, false);
        }

        renderIqamaState(prayerName, remainingSeconds, offsetMinutes) {
            this.widget.classList.add('iqama-active');
            this.modeBadge.textContent = "IQAMA COUNTDOWN";
            this.targetName.textContent = prayerName.toUpperCase();

            const mins = Math.floor(remainingSeconds / 60);
            const secs = remainingSeconds % 60;
            this.timerValue.textContent = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
            this.subtext.textContent = `Congregation prayer in ${mins}m ${secs}s (Offset: +${offsetMinutes}m)`;
        }

        renderNextPrayerState(prayer, remainingSeconds) {
            this.widget.classList.remove('iqama-active');
            this.modeBadge.textContent = "NEXT PRAYER";
            this.targetName.textContent = prayer.name.toUpperCase();

            const hours = Math.floor(remainingSeconds / 3600);
            const mins = Math.floor((remainingSeconds % 3600) / 60);
            const secs = remainingSeconds % 60;

            this.timerValue.textContent = `${String(hours).padStart(2, '0')}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;

            const formattedTime = formatTimeWithPreference(prayer.timeString);
            this.subtext.textContent = `Adhan starts at ${formattedTime}`;
        }

        updatePrayerCards(prayerList, targetPrayerName, isIqama) {
            const now = new Date();

            prayerList.forEach(p => {
                const card = document.getElementById(`card-${p.name.toLowerCase()}`);
                const chip = document.getElementById(`chip-${p.name.toLowerCase()}`);
                const timeEl = document.getElementById(`time-${p.name.toLowerCase()}`);
                if (!card || !chip || !timeEl) return;

                timeEl.textContent = formatTimeWithPreference(p.timeString);

                // Reset classes
                card.classList.remove('is-next', 'is-current', 'is-passed');
                chip.classList.remove('upcoming', 'current', 'passed');

                if (p.name.toLowerCase() === targetPrayerName.toLowerCase()) {
                    if (isIqama) {
                        card.classList.add('is-current');
                        chip.classList.add('current');
                        chip.textContent = "IQAMA NOW";
                    } else {
                        card.classList.add('is-next');
                        chip.classList.add('upcoming');
                        chip.textContent = "Upcoming";
                    }
                } else if (now > p.timeDate) {
                    card.classList.add('is-passed');
                    chip.classList.add('passed');
                    chip.textContent = "Passed";
                } else {
                    chip.textContent = "Upcoming";
                }
            });
        }
    }

    // Helper: Convert "17:02" to 12h "05:02 PM" or 24h "17:02" based on preference
    function formatTimeWithPreference(time24Str) {
        if (!time24Str) return "";
        const [h, m] = time24Str.split(':').map(Number);
        if (STATE.timeFormat24) {
            return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
        }
        const ampm = h >= 12 ? 'PM' : 'AM';
        const h12 = h % 12 || 12;
        return `${String(h12).padStart(2, '0')}:${String(m).padStart(2, '0')} ${ampm}`;
    }

    // =========================================================================
    // 6. UI SYNCHRONIZER & TOAST MESSAGING
    // =========================================================================
    function updateClockDisplay() {
        const now = new Date();
        let hours = now.getHours();
        const minutes = String(now.getMinutes()).padStart(2, '0');
        const ampm = hours >= 12 ? 'PM' : 'AM';

        if (!STATE.timeFormat24) {
            hours = hours % 12 || 12;
        }

        const clockEl = document.getElementById('digital-clock');
        const ampmEl = document.getElementById('digital-ampm');
        if (clockEl) clockEl.textContent = `${String(hours).padStart(2, '0')}:${minutes}`;
        if (ampmEl) ampmEl.textContent = STATE.timeFormat24 ? '' : ampm;

        // Greeting update: "Good Evening", "Good Morning", etc.
        const greetingEl = document.getElementById('greeting-text');
        if (greetingEl) greetingEl.textContent = getDynamicGreeting();

        // Gregorian & Hijri dates
        const gregEl = document.getElementById('gregorian-date-text');
        if (gregEl) gregEl.textContent = getFormattedGregorianDate();

        const hijriEl = document.getElementById('hijri-date-text');
        if (hijriEl) hijriEl.textContent = getFormattedHijriDate(STATE.hijriOffset);

        // Location text
        const locEl = document.getElementById('current-location-text');
        if (locEl) locEl.textContent = STATE.locations[STATE.currentLocationIndex].name;
    }

    function updateWeatherDisplay() {
        const profile = WEATHER_PROFILES[currentWeatherIndex];
        const tempEl = document.getElementById('weather-temp');
        const condEl = document.getElementById('weather-condition');
        const iconSlot = document.getElementById('weather-icon-slot');
        const widget = document.getElementById('weather-widget');

        if (!STATE.showWeather) {
            if (widget) widget.style.display = 'none';
            return;
        } else if (widget) {
            widget.style.display = 'inline-flex';
        }

        let tempVal = profile.tempC;
        let unitSymbol = "°C";
        if (!STATE.tempUnitCelsius) {
            tempVal = Math.round((profile.tempC * 9 / 5) + 32);
            unitSymbol = "°F";
        }

        const cityEl = document.getElementById('weather-city');
        if (cityEl) cityEl.textContent = STATE.city || 'Doha';
        if (tempEl) tempEl.textContent = `${tempVal}${unitSymbol}`;
        if (condEl) condEl.textContent = profile.condition;
        if (iconSlot) iconSlot.innerHTML = getWeatherSvg(profile.icon);
    }

    function showToast(message) {
        const container = document.getElementById('toast-container');
        if (!container) return;

        const toast = document.createElement('div');
        toast.className = 'tv-toast';
        toast.innerHTML = `
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:20px;height:20px;color:var(--accent-cyan)">
                <circle cx="12" cy="12" r="10"></circle>
                <line x1="12" y1="16" x2="12" y2="12"></line>
                <line x1="12" y1="8" x2="12.01" y2="8"></line>
            </svg>
            <span>${message}</span>
        `;
        container.appendChild(toast);

        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateY(15px)';
            toast.style.transition = 'all 0.25s ease';
            setTimeout(() => toast.remove(), 250);
        }, 3000);
    }

    // =========================================================================
    // 7. MODALS & SETTINGS CONTROLLERS
    // =========================================================================
    function openModal(modalId) {
        closeAllModals();
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.classList.add('open');
            modal.setAttribute('aria-hidden', 'false');
            STATE.activeModal = modalId;

            // Auto-focus first focusable inside modal
            const firstFocusable = modal.querySelector('.focusable');
            if (firstFocusable) firstFocusable.focus();
        }
    }

    function closeAllModals() {
        document.querySelectorAll('.tv-modal-backdrop').forEach(modal => {
            modal.classList.remove('open');
            modal.setAttribute('aria-hidden', 'true');
        });
        STATE.activeModal = null;
    }

    function setupModals() {
        // Close buttons inside modals
        document.getElementById('btn-close-settings')?.addEventListener('click', closeAllModals);
        document.getElementById('btn-close-yt')?.addEventListener('click', closeAllModals);

        // Click outside on backdrop to close
        document.querySelectorAll('.tv-modal-backdrop').forEach(backdrop => {
            backdrop.addEventListener('click', (e) => {
                if (e.target === backdrop) closeAllModals();
            });
        });

        // Footer buttons triggers
        document.getElementById('btn-inputs-modal')?.addEventListener('click', () => openModal('inputs-modal'));
        document.getElementById('btn-youtube-modal')?.addEventListener('click', () => openModal('youtube-modal'));
        document.getElementById('btn-settings-modal')?.addEventListener('click', () => openModal('settings-modal'));

        // YouTube app launch action
        document.getElementById('btn-launch-yt-app')?.addEventListener('click', () => {
            showToast("Launching YouTube TV Player in 4K HDR...");
            setTimeout(closeAllModals, 1200);
        });

        // App cards launch simulation
        document.querySelectorAll('.app-card').forEach(card => {
            card.addEventListener('click', () => {
                const appName = card.dataset.app;
                if (appName === 'youtube') {
                    openModal('youtube-modal');
                } else {
                    showToast(`Launching ${appName.toUpperCase()} App...`);
                }
            });
        });

        // Settings items click / select handlers
        setupSettingsInteractions();
    }

    // Interactive Settings Rows adhering strictly to the 4 reorganized tiers:
    // 1. Prayer Time Settings (top)
    // 2. Weather Settings
    // 3. Screen Saver Settings
    // 4. General Launcher Settings
    function setupSettingsInteractions() {
        // 1. PRAYER TIME SETTINGS
        document.querySelector('[data-action="toggle-prayer-bar"]')?.addEventListener('click', () => {
            STATE.showPrayerBar = !STATE.showPrayerBar;
            const bar = document.querySelector('.prayer-bar-section');
            const valEl = document.getElementById('val-prayer-bar');
            if (bar) bar.style.display = STATE.showPrayerBar ? 'block' : 'none';
            if (valEl) {
                valEl.textContent = STATE.showPrayerBar ? 'ON' : 'OFF';
                valEl.className = `settings-value ${STATE.showPrayerBar ? 'toggle-on' : 'toggle-off'}`;
            }
            showToast(`Prayer Bar: ${STATE.showPrayerBar ? 'Enabled' : 'Hidden'}`);
        });

        // Test Iqama Switch (Live Iqama simulation for 60 seconds)
        document.querySelector('[data-action="test-iqama-switch"]')?.addEventListener('click', () => {
            const prayers = ["Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"];
            const chosen = prayers[Math.floor(Math.random() * prayers.length)];
            const offset = IQAMA_OFFSETS_MINUTES[chosen];

            STATE.testIqamaSimulation = {
                prayerName: chosen,
                endTime: Date.now() + 60 * 1000, // 60 seconds simulation
                offsetMinutes: offset
            };

            closeAllModals();
            showToast(`⚡ Iqama Simulation Started for ${chosen} (+${offset}m offset)`);
        });

        // Cycle Calculation Location
        document.querySelector('[data-action="cycle-location"]')?.addEventListener('click', () => {
            STATE.currentLocationIndex = (STATE.currentLocationIndex + 1) % STATE.locations.length;
            const loc = STATE.locations[STATE.currentLocationIndex];
            const valEl = document.getElementById('val-location');
            if (valEl) valEl.textContent = loc.name;
            updateClockDisplay();
            showToast(`Calculation Location: ${loc.name}`);
        });

        // Cycle Calculation Method
        document.querySelector('[data-action="cycle-method"]')?.addEventListener('click', () => {
            STATE.currentMethodIndex = (STATE.currentMethodIndex + 1) % STATE.methods.length;
            const m = STATE.methods[STATE.currentMethodIndex];
            const valEl = document.getElementById('val-method');
            if (valEl) valEl.textContent = m;
            showToast(`Method: ${m}`);
        });

        // Toggle Time Format (12h vs 24h)
        document.querySelector('[data-action="toggle-time-format"]')?.addEventListener('click', () => {
            STATE.timeFormat24 = !STATE.timeFormat24;
            const valEl = document.getElementById('val-time-format');
            if (valEl) valEl.textContent = STATE.timeFormat24 ? "24-Hour (Military)" : "12-Hour (AM/PM)";
            updateClockDisplay();
            showToast(`Time Format: ${STATE.timeFormat24 ? "24-Hour" : "12-Hour"}`);
        });

        // Cycle Adhan Sound
        document.querySelector('[data-action="cycle-azan-audio"]')?.addEventListener('click', () => {
            STATE.currentAzanIndex = (STATE.currentAzanIndex + 1) % STATE.azanSounds.length;
            const sound = STATE.azanSounds[STATE.currentAzanIndex];
            const valEl = document.getElementById('val-azan-sound');
            if (valEl) valEl.textContent = sound;
            showToast(`Adhan Sound: ${sound}`);
        });

        // Cycle Hijri Moon Sighting Offset
        document.querySelector('[data-action="cycle-hijri-offset"]')?.addEventListener('click', () => {
            STATE.hijriOffset = (STATE.hijriOffset + 2) % 5 - 2; // cycles -2, -1, 0, 1, 2
            const valEl = document.getElementById('val-hijri-offset');
            if (valEl) valEl.textContent = `${STATE.hijriOffset >= 0 ? '+' : ''}${STATE.hijriOffset} Days`;
            updateClockDisplay();
            showToast(`Hijri Moon Offset: ${STATE.hijriOffset} Days`);
        });

        // 2. WEATHER SETTINGS
        document.querySelector('[data-action="toggle-weather-display"]')?.addEventListener('click', () => {
            STATE.showWeather = !STATE.showWeather;
            const valEl = document.getElementById('val-weather-show');
            if (valEl) {
                valEl.textContent = STATE.showWeather ? 'ON' : 'OFF';
                valEl.className = `settings-value ${STATE.showWeather ? 'toggle-on' : 'toggle-off'}`;
            }
            updateWeatherDisplay();
            showToast(`Weather Widget: ${STATE.showWeather ? 'Visible' : 'Hidden'}`);
        });

        document.querySelector('[data-action="auto-detect-location"]')?.addEventListener('click', async () => {
            showToast("Detecting location via IP…");
            try {
                const res = await fetch("https://ipwho.is/");
                const data = await res.json();
                if (data && data.success && data.city) {
                    STATE.city = data.city;
                    STATE.country = data.country || "";
                    STATE.location = `${data.city}, ${data.country || ""}`;
                    const locEl = document.getElementById('current-location-text');
                    if (locEl) locEl.textContent = STATE.location;
                    const wLocEl = document.getElementById('val-weather-location');
                    if (wLocEl) wLocEl.textContent = STATE.location;
                    const pLocEl = document.getElementById('val-location');
                    if (pLocEl) pLocEl.textContent = STATE.location;
                    updateWeatherDisplay();
                    fetchPrayerTimes();
                    showToast(`Location detected: ${STATE.location}`);
                } else {
                    showToast("Could not detect location. Using default.");
                }
            } catch (err) {
                showToast("Auto-detect failed. Please check network.");
            }
        });

        document.querySelector('[data-action="change-weather-location"]')?.addEventListener('click', () => {
            const cities = [
                { city: "Doha", country: "Qatar" },
                { city: "Riyadh", country: "Saudi Arabia" },
                { city: "Makkah", country: "Saudi Arabia" },
                { city: "Madinah", country: "Saudi Arabia" },
                { city: "Dubai", country: "United Arab Emirates" },
                { city: "Abu Dhabi", country: "United Arab Emirates" },
                { city: "Kuwait City", country: "Kuwait" },
                { city: "Cairo", country: "Egypt" },
                { city: "Istanbul", country: "Turkey" },
                { city: "London", country: "United Kingdom" }
            ];
            const currentIdx = cities.findIndex(c => c.city.toLowerCase() === STATE.city.toLowerCase());
            const next = cities[(currentIdx + 1) % cities.length];
            STATE.city = next.city;
            STATE.country = next.country;
            STATE.location = `${next.city}, ${next.country}`;
            const locEl = document.getElementById('current-location-text');
            if (locEl) locEl.textContent = STATE.location;
            const wLocEl = document.getElementById('val-weather-location');
            if (wLocEl) wLocEl.textContent = STATE.location;
            const pLocEl = document.getElementById('val-location');
            if (pLocEl) pLocEl.textContent = STATE.location;
            updateWeatherDisplay();
            fetchPrayerTimes();
            showToast(`Weather location: ${STATE.location}`);
        });

        document.querySelector('[data-action="toggle-temp-unit"]')?.addEventListener('click', () => {
            STATE.tempUnitCelsius = !STATE.tempUnitCelsius;
            const valEl = document.getElementById('val-temp-unit');
            if (valEl) valEl.textContent = STATE.tempUnitCelsius ? "Celsius (°C)" : "Fahrenheit (°F)";
            updateWeatherDisplay();
            showToast(`Unit: ${STATE.tempUnitCelsius ? "Celsius (°C)" : "Fahrenheit (°F)"}`);
        });

        document.querySelector('[data-action="refresh-weather-now"]')?.addEventListener('click', () => {
            currentWeatherIndex = (currentWeatherIndex + 1) % WEATHER_PROFILES.length;
            updateWeatherDisplay();
            showToast("Weather telemetry updated successfully.");
        });

        // 3. SCREEN SAVER SETTINGS
        document.querySelector('[data-action="toggle-screensaver-enabled"]')?.addEventListener('click', () => {
            STATE.screensaverEnabled = !STATE.screensaverEnabled;
            const valEl = document.getElementById('val-screensaver-enabled');
            if (valEl) valEl.textContent = STATE.screensaverEnabled ? "ON" : "OFF";
            showToast(`Screen Saver: ${STATE.screensaverEnabled ? "ON" : "OFF"}`);
        });

        document.querySelector('[data-action="cycle-screensaver-timeout"]')?.addEventListener('click', () => {
            const timeouts = [1, 2, 5, 10, 15, 20, 30, 60];
            const currentIdx = timeouts.indexOf(STATE.screensaverTimeoutMinutes);
            STATE.screensaverTimeoutMinutes = timeouts[(currentIdx + 1) % timeouts.length];
            const valEl = document.getElementById('val-screensaver-timeout');
            if (valEl) valEl.textContent = `${STATE.screensaverTimeoutMinutes} Minutes`;
            showToast(`Screen Saver Timeout: ${STATE.screensaverTimeoutMinutes}m`);
        });

        document.querySelector('[data-action="cycle-screensaver-style"]')?.addEventListener('click', () => {
            STATE.currentScreensaverThemeIndex = (STATE.currentScreensaverThemeIndex + 1) % STATE.screensaverThemes.length;
            const theme = STATE.screensaverThemes[STATE.currentScreensaverThemeIndex];
            const valEl = document.getElementById('val-screensaver-style');
            if (valEl) valEl.textContent = theme;
            showToast(`Screen Saver App: ${theme}`);
        });

        document.querySelector('[data-action="trigger-screensaver-now"]')?.addEventListener('click', () => {
            closeAllModals();
            window.screensaverInstance?.start();
        });

        // 4. GENERAL LAUNCHER SETTINGS
        // Controls strictly for Background Pixel Density & Aurora Glow without altering the earlier TV background
        document.querySelector('[data-action="cycle-bg-density"]')?.addEventListener('click', () => {
            STATE.currentBgDensityIndex = (STATE.currentBgDensityIndex + 1) % STATE.bgDensityModes.length;
            const modeName = STATE.bgDensityModes[STATE.currentBgDensityIndex];
            const valEl = document.getElementById('val-bg-density');
            if (valEl) valEl.textContent = modeName;
            window.bgInstance?.resize();
            showToast(`Background Pixel Density: ${modeName}`);
        });

        document.querySelector('[data-action="cycle-aurora-intensity"]')?.addEventListener('click', () => {
            STATE.currentAuroraIntensityIndex = (STATE.currentAuroraIntensityIndex + 1) % STATE.auroraIntensities.length;
            const intensityObj = STATE.auroraIntensities[STATE.currentAuroraIntensityIndex];
            const valEl = document.getElementById('val-aurora-intensity');
            if (valEl) valEl.textContent = intensityObj.label;
            showToast(`Aurora Intensity: ${intensityObj.label}`);
        });

        document.querySelector('[data-action="toggle-glass-effects"]')?.addEventListener('click', () => {
            STATE.glassEffectsOn = !STATE.glassEffectsOn;
            const valEl = document.getElementById('val-glass-effects');
            if (valEl) {
                valEl.textContent = STATE.glassEffectsOn ? 'ON (16px)' : 'OFF (Solid)';
                valEl.className = `settings-value ${STATE.glassEffectsOn ? 'toggle-on' : 'toggle-off'}`;
            }
            document.body.classList.toggle('disable-glass', !STATE.glassEffectsOn);
            showToast(`Glassmorphic Blur: ${STATE.glassEffectsOn ? '16px Blur Active' : 'Solid Opaque'}`);
        });

        document.querySelector('[data-action="reset-launcher-defaults"]')?.addEventListener('click', () => {
            STATE.timeFormat24 = false;
            STATE.hijriOffset = 0;
            STATE.currentLocationIndex = 0;
            STATE.currentBgDensityIndex = 0;
            STATE.currentAuroraIntensityIndex = 0;
            STATE.showPrayerBar = true;
            STATE.showWeather = true;
            STATE.tempUnitCelsius = true;
            window.bgInstance?.resize();
            updateClockDisplay();
            updateWeatherDisplay();
            showToast("Launcher settings restored to defaults.");
        });
    }

    // =========================================================================
    // 8. HARDWARE TV REMOTE KEY LISTENERS & 2D SPATIAL D-PAD NAVIGATION
    // =========================================================================
    /**
     * Requirement:
     * - Settings Button (KeySettings, Set or F1/code 121): Directly toggles the Launcher Settings overlay.
     * - Inputs Button (KeyInput, Source or F2/code 122): Triggers the TV Inputs modal.
     * - YouTube Dedicated Button (KeyYouTube or code 10009 / media shortcut key): Launches the YouTube modal/app directly.
     * - D-Pad & Navigation: Support Standard Arrow keys (ArrowUp, ArrowDown, ArrowLeft, ArrowRight),
     *   Enter/Select, and Back/Escape.
     */
    class RemoteNavigationEngine {
        constructor() {
            this.currentFocusElement = null;
            this.init();
        }

        init() {
            window.addEventListener('keydown', (e) => this.handleKeyDown(e));

            // Set initial focus on the first app card on startup
            setTimeout(() => {
                const initialFocus = document.querySelector('.apps-grid .focusable');
                if (initialFocus) {
                    this.setFocus(initialFocus);
                }
            }, 300);
        }

        setFocus(el) {
            if (!el) return;
            if (this.currentFocusElement) {
                this.currentFocusElement.classList.remove('focused');
            }
            this.currentFocusElement = el;
            el.classList.add('focused');
            el.focus({ preventScroll: false });

            // Ensure focused element is scrolled into view if inside settings drawer
            el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }

        handleKeyDown(e) {
            // Any key press resets screensaver timer & wakes up screensaver
            STATE.lastActivityTime = Date.now();
            if (STATE.isScreensaverActive) {
                e.preventDefault();
                window.screensaverInstance?.stop();
                return;
            }

            const key = e.key;
            const code = e.keyCode || e.which;

            // 1. HARDWARE KEY: Launcher Settings (F1, code 121, KeySettings, Set)
            if (key === 'F1' || code === 121 || key === 'Settings' || key === 'KeySettings' || key === 'Set') {
                e.preventDefault();
                if (STATE.activeModal === 'settings-modal') {
                    closeAllModals();
                } else {
                    openModal('settings-modal');
                }
                return;
            }

            // 2. HARDWARE KEY: TV Inputs (F2, code 122, KeyInput, Source)
            if (key === 'F2' || code === 122 || key === 'Input' || key === 'KeyInput' || key === 'Source') {
                e.preventDefault();
                if (STATE.activeModal === 'inputs-modal') {
                    closeAllModals();
                } else {
                    openModal('inputs-modal');
                }
                return;
            }

            // 3. HARDWARE KEY: YouTube Dedicated Button (KeyYouTube, code 10009, MediaSelect)
            if (key === 'YouTube' || key === 'KeyYouTube' || code === 10009 || key === 'MediaSelect') {
                e.preventDefault();
                if (STATE.activeModal === 'youtube-modal') {
                    closeAllModals();
                } else {
                    openModal('youtube-modal');
                }
                return;
            }

            // 4. HARDWARE KEY: Back / Return / Escape (Escape, code 27, code 10008, Backspace)
            if (key === 'Escape' || code === 27 || key === 'Back' || key === 'GoBack' || code === 10008) {
                e.preventDefault();
                if (STATE.activeModal) {
                    closeAllModals();
                    // Return focus to first app
                    const app = document.querySelector('.apps-grid .focusable');
                    if (app) this.setFocus(app);
                }
                return;
            }

            // 5. HARDWARE KEY: Enter / Select / OK (Enter, code 13)
            if (key === 'Enter' || code === 13) {
                e.preventDefault();
                const active = document.activeElement || this.currentFocusElement;
                if (active) {
                    active.click();
                }
                return;
            }

            // 6. D-PAD SPATIAL NAVIGATION (ArrowUp, ArrowDown, ArrowLeft, ArrowRight)
            if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(key)) {
                e.preventDefault();
                this.navigateDirection(key);
            }
        }

        navigateDirection(direction) {
            // Determine active scope (modal vs main launcher)
            let scope = document;
            if (STATE.activeModal) {
                scope = document.getElementById(STATE.activeModal);
                if (!scope) return;
            }

            const focusables = Array.from(scope.querySelectorAll('.focusable:not([disabled])'))
                .filter(el => {
                    const rect = el.getBoundingClientRect();
                    return rect.width > 0 && rect.height > 0 && window.getComputedStyle(el).display !== 'none';
                });

            if (focusables.length === 0) return;

            const current = document.activeElement && focusables.includes(document.activeElement)
                ? document.activeElement
                : this.currentFocusElement || focusables[0];

            const currentRect = current.getBoundingClientRect();
            const currentCenter = {
                x: currentRect.left + currentRect.width / 2,
                y: currentRect.top + currentRect.height / 2
            };

            let bestCandidate = null;
            let minDistance = Infinity;

            focusables.forEach(candidate => {
                if (candidate === current) return;
                const rect = candidate.getBoundingClientRect();
                const center = {
                    x: rect.left + rect.width / 2,
                    y: rect.top + rect.height / 2
                };

                const dx = center.x - currentCenter.x;
                const dy = center.y - currentCenter.y;

                let isValidDirection = false;
                let primaryDist = 0;
                let secondaryDist = 0;

                switch (direction) {
                    case 'ArrowRight':
                        isValidDirection = dx > 15;
                        primaryDist = dx;
                        secondaryDist = Math.abs(dy);
                        break;
                    case 'ArrowLeft':
                        isValidDirection = dx < -15;
                        primaryDist = -dx;
                        secondaryDist = Math.abs(dy);
                        break;
                    case 'ArrowDown':
                        isValidDirection = dy > 15;
                        primaryDist = dy;
                        secondaryDist = Math.abs(dx);
                        break;
                    case 'ArrowUp':
                        isValidDirection = dy < -15;
                        primaryDist = -dy;
                        secondaryDist = Math.abs(dx);
                        break;
                }

                if (isValidDirection) {
                    // Weighted Manhattan distance prioritizing alignment in movement axis
                    const distance = primaryDist + (secondaryDist * 2.2);
                    if (distance < minDistance) {
                        minDistance = distance;
                        bestCandidate = candidate;
                    }
                }
            });

            if (bestCandidate) {
                this.setFocus(bestCandidate);
            }
        }
    }

    // =========================================================================
    // 9. APP INITIALIZATION & HEARTBEAT LOOP
    // =========================================================================
    document.addEventListener('DOMContentLoaded', () => {
        // 1. Initialize High-Density Earlier TV Aurora Background
        window.bgInstance = new HighDensityAuroraBackground('ambient-canvas');

        // 2. Initialize Screensaver
        window.screensaverInstance = new ScreensaverEngine();

        // 3. Initialize Prayer Countdown Engine
        const prayerEngine = new PrayerCountdownEngine();

        // 4. Initialize Modals and Settings
        setupModals();

        // 5. Initialize Hardware Remote Navigation
        window.remoteNav = new RemoteNavigationEngine();

        // 6. Initial render pass
        updateClockDisplay();
        updateWeatherDisplay();
        prayerEngine.tick();

        // 7. Periodic High-Frequency Heartbeat Loop (every second)
        setInterval(() => {
            updateClockDisplay();
            prayerEngine.tick();

            // Idle timer check for screensaver
            if (STATE.screensaverEnabled && STATE.screensaverTimeoutMinutes > 0 && !STATE.isScreensaverActive) {
                const idleMs = Date.now() - STATE.lastActivityTime;
                if (idleMs > STATE.screensaverTimeoutMinutes * 60 * 1000) {
                    window.screensaverInstance.start();
                }
            }
        }, 1000);

        console.log("Smart TV Launcher Engine Initialized with 2K/4K High-Density Aurora Background.");
    });

})();
