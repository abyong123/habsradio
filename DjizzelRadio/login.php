<?php
if (!defined('BOOM')) {
    die();
}

$dj_cache = isset($bbfv) ? $bbfv : '?v=5.0.0';

$dj_title = trim((string)($setting['title'] ?? 'Djizzel Radio'));
if ($dj_title === '') {
    $dj_title = 'Djizzel Radio';
}

$dj_description = trim((string)($setting['description'] ?? 'Luister naar live dj’s, ontdek nieuwe muziek en ontmoet andere muziekliefhebbers.'));
if ($dj_description === '') {
    $dj_description = 'Luister naar live dj’s, ontdek nieuwe muziek en ontmoet andere muziekliefhebbers.';
}

$dj_title_safe = htmlspecialchars($dj_title, ENT_QUOTES, 'UTF-8');
$dj_description_safe = htmlspecialchars($dj_description, ENT_QUOTES, 'UTF-8');

$dj_stream_url = trim((string)($player['stream_url'] ?? ''));
$dj_stream_name = trim((string)($player['stream_alias'] ?? 'Djizzel Radio Live'));
if ($dj_stream_name === '') {
    $dj_stream_name = 'Djizzel Radio Live';
}

$dj_stream_url_safe = htmlspecialchars($dj_stream_url, ENT_QUOTES, 'UTF-8');
$dj_stream_name_safe = htmlspecialchars($dj_stream_name, ENT_QUOTES, 'UTF-8');

$dj_root = defined('BOOM_PATH') ? rtrim(BOOM_PATH, '/\\') : dirname(__DIR__, 3);
$dj_logo_png_disk = $dj_root . '/control/login/DjizzelRadio/assets/djizzel-radio-logo.png';
$dj_logo = is_file($dj_logo_png_disk)
    ? 'control/login/DjizzelRadio/assets/djizzel-radio-logo.png'
    : 'control/login/DjizzelRadio/assets/djizzel-radio-logo.svg';
$dj_logo_safe = htmlspecialchars($dj_logo, ENT_QUOTES, 'UTF-8');

$dj_allow_registration = function_exists('registration') ? (bool)registration() : true;
$dj_allow_guest = function_exists('allowGuest') ? (bool)allowGuest() : false;
$dj_external_bridge = function_exists('bridgeMode') ? (bool)bridgeMode(1) : false;
?>
<link rel="stylesheet" type="text/css" href="control/login/DjizzelRadio/login.css<?php echo $dj_cache; ?>">

<div id="djizzel_login" class="dj-page" data-title="<?php echo $dj_title_safe; ?>">
    <div class="dj-ambient" aria-hidden="true">
        <span class="dj-orb dj-orb-one"></span>
        <span class="dj-orb dj-orb-two"></span>
        <span class="dj-grid"></span>
        <span id="dj_particles" class="dj-particles"></span>
    </div>

    <header class="dj-header">
        <div class="dj-container dj-header-inner">
            <a class="dj-brand" href="./" aria-label="<?php echo $dj_title_safe; ?>">
                <img src="<?php echo $dj_logo_safe; ?>" alt="<?php echo $dj_title_safe; ?>">
            </a>

            <div class="dj-header-status" aria-label="Radio status">
                <span class="dj-live-dot"></span>
                <span>Live muziek &amp; chat</span>
            </div>
        </div>
    </header>

    <main>
        <section class="dj-hero">
            <div class="dj-container dj-hero-grid">
                <div class="dj-hero-copy">
                    <span class="dj-eyebrow"><i class="fa fa-headphones"></i> Muziek brengt ons samen</span>
                    <h1><?php echo $dj_title_safe; ?></h1>
                    <p class="dj-site-description"><?php echo $dj_description_safe; ?></p>
                    <p class="dj-supporting-copy">Chat mee, luister naar de radio en beleef live dj-sets met een community die net zoveel van muziek houdt als jij.</p>

                    <div class="dj-account-actions">
                        <?php if ($dj_external_bridge && function_exists('bridgeLogin') && function_exists('getChatPath')) { ?>
                            <div class="dj-bridge-login">
                                <?php echo bridgeLogin(getChatPath()); ?>
                            </div>
                        <?php } else { ?>
                            <button type="button" class="dj-btn dj-btn-primary boom_request" data-boom="box/login">
                                <i class="fa fa-sign-in"></i>
                                <span>Inloggen</span>
                            </button>

                            <?php if ($dj_allow_registration) { ?>
                                <button type="button" class="dj-btn dj-btn-secondary boom_request" data-boom="box/registration">
                                    <i class="fa fa-user-plus"></i>
                                    <span>Account aanmaken</span>
                                </button>
                            <?php } ?>

                            <?php if ($dj_allow_guest) { ?>
                                <button type="button" class="dj-btn-link boom_request" data-boom="box/guest_login">
                                    Doorgaan als gast
                                    <i class="fa fa-angle-right"></i>
                                </button>
                            <?php } ?>
                        <?php } ?>
                    </div>

                    <div class="dj-trust-row" aria-label="Communityvoordelen">
                        <span><i class="fa fa-check-circle"></i> Gratis deelnemen</span>
                        <span><i class="fa fa-check-circle"></i> Live dj’s</span>
                        <span><i class="fa fa-check-circle"></i> Muziekliefhebbers</span>
                    </div>
                </div>

                <aside class="dj-player-card" aria-label="Djizzel Radio speler">
                    <div class="dj-player-top">
                        <div class="dj-player-label">
                            <span class="dj-live-dot"></span>
                            Nu live
                        </div>
                        <button type="button" id="dj_mute" class="dj-icon-btn" aria-label="Geluid dempen">
                            <i class="fa fa-volume-up"></i>
                        </button>
                    </div>

                    <div class="dj-record-wrap" aria-hidden="true">
                        <div id="dj_record" class="dj-record">
                            <span class="dj-record-ring"></span>
                            <span class="dj-record-label"><i class="fa fa-music"></i></span>
                        </div>
                        <div id="dj_equalizer" class="dj-equalizer">
                            <i></i><i></i><i></i><i></i><i></i><i></i><i></i>
                        </div>
                    </div>

                    <div class="dj-track-info">
                        <small>DJIZZEL RADIO</small>
                        <h2><?php echo $dj_stream_name_safe; ?></h2>
                        <p id="dj_player_status">Klaar om af te spelen</p>
                    </div>

                    <div class="dj-player-controls">
                        <button type="button" id="dj_play" class="dj-play-btn" aria-label="Radio afspelen">
                            <i class="fa fa-play"></i>
                        </button>

                        <div class="dj-volume-control">
                            <i class="fa fa-volume-down"></i>
                            <input id="dj_volume" type="range" min="0" max="1" step="0.05" value="0.75" aria-label="Volume">
                            <i class="fa fa-volume-up"></i>
                        </div>
                    </div>

                    <?php if ($dj_stream_url !== '') { ?>
                        <audio id="dj_audio" preload="none" data-stream="<?php echo $dj_stream_url_safe; ?>"></audio>
                    <?php } else { ?>
                        <div class="dj-player-warning">Configureer eerst de radiostream in CodyChat.</div>
                    <?php } ?>
                </aside>
            </div>
        </section>

        <section class="dj-members-section">
            <div class="dj-container">
                <div class="dj-section-heading">
                    <div>
                        <span class="dj-section-kicker">De community is actief</span>
                        <h2>Ontmoet mensen die nu luisteren</h2>
                        <p>Bekijk recent actieve leden en stap meteen binnen in de chat.</p>
                    </div>
                    <div class="dj-member-count"><strong id="dj_online_count">0</strong><span>online</span></div>
                </div>

                <div class="dj-members-shell">
                    <button type="button" id="dj_users_prev" class="dj-slider-btn" aria-label="Vorige leden"><i class="fa fa-angle-left"></i></button>
                    <div id="dj_active_users" class="dj-active-users" aria-live="polite">
                        <div class="dj-users-loading"><span></span><span></span><span></span></div>
                    </div>
                    <button type="button" id="dj_users_next" class="dj-slider-btn" aria-label="Volgende leden"><i class="fa fa-angle-right"></i></button>
                </div>
            </div>
        </section>

        <section class="dj-features">
            <div class="dj-container dj-feature-grid">
                <article class="dj-feature-card">
                    <span class="dj-feature-icon"><i class="fa fa-comments"></i></span>
                    <div><h3>Echte gesprekken</h3><p>Praat ontspannen met nieuwe mensen terwijl de muziek blijft spelen.</p></div>
                </article>
                <article class="dj-feature-card">
                    <span class="dj-feature-icon"><i class="fa fa-microphone"></i></span>
                    <div><h3>Live dj-ervaring</h3><p>Luister naar live shows, verzoeknummers en verrassende muziekmomenten.</p></div>
                </article>
                <article class="dj-feature-card">
                    <span class="dj-feature-icon"><i class="fa fa-users"></i></span>
                    <div><h3>Eén muziekcommunity</h3><p>Vind luisteraars met dezelfde smaak en ontdek samen nieuwe tracks.</p></div>
                </article>
            </div>
        </section>
    </main>

    <footer class="dj-footer">
        <div class="dj-container dj-footer-main">
            <a class="dj-footer-brand" href="./">
                <img src="<?php echo $dj_logo_safe; ?>" alt="<?php echo $dj_title_safe; ?>">
            </a>

            <nav class="dj-footer-nav" aria-label="Voettekstnavigatie">
                <a href="./">Home</a>
                <a href="terms.php">Gebruiksvoorwaarden</a>
                <a href="privacy.php">Privacybeleid</a>
                <a href="rules.php">Regels</a>
                <a href="contact_us.php">Contact</a>
                <button type="button" class="dj-language-btn boom_request" data-boom="box/language">
                    <i class="fa fa-language"></i> Taal
                </button>
            </nav>
        </div>

        <div class="dj-container dj-footer-bottom">
            <p>&copy; <?php echo date('Y'); ?> <?php echo $dj_title_safe; ?>. Alle rechten voorbehouden.</p>
            <p>Live muziek. Echte gesprekken. Eén community.</p>
        </div>
    </footer>
</div>

<?php if (function_exists('boomTemplate')) { echo boomTemplate('element/cookie'); } ?>
<script data-cfasync="false">
window.DjizzelLoginConfig = {
    activeUsersUrl: 'control/login/DjizzelRadio/fetch_active_users.php',
    refreshInterval: 30000,
    streamConfigured: <?php echo $dj_stream_url !== '' ? 'true' : 'false'; ?>
};
</script>
<script data-cfasync="false" src="control/login/DjizzelRadio/assets/djizzel-login.js<?php echo $dj_cache; ?>"></script>
