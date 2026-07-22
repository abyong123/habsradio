(function () {
    'use strict';

    var config = window.DjizzelLoginConfig || {};
    var root = document.getElementById('djizzel_login');

    document.body.classList.add('djizzel-login-body');

    function qs(selector, context) {
        return (context || document).querySelector(selector);
    }

    function qsa(selector, context) {
        return Array.prototype.slice.call((context || document).querySelectorAll(selector));
    }

    function createParticles() {
        var holder = qs('#dj_particles');
        if (!holder || holder.children.length) {
            return;
        }

        for (var i = 0; i < 26; i += 1) {
            var ember = document.createElement('span');
            ember.className = 'dj-particle';
            ember.style.left = (4 + Math.random() * 92) + '%';
            ember.style.top = (25 + Math.random() * 68) + '%';
            ember.style.setProperty('--duration', (8 + Math.random() * 11).toFixed(2) + 's');
            ember.style.setProperty('--delay', (-Math.random() * 14).toFixed(2) + 's');
            ember.style.setProperty('--drift', (-45 + Math.random() * 90).toFixed(0) + 'px');
            holder.appendChild(ember);
        }
    }

    function setupPlayer() {
        var audio = qs('#dj_audio');
        var playButton = qs('#dj_play');
        var muteButton = qs('#dj_mute');
        var volume = qs('#dj_volume');
        var record = qs('#dj_record');
        var equalizer = qs('#dj_equalizer');
        var status = qs('#dj_player_status');

        if (!playButton) {
            return;
        }

        var stream = audio ? audio.getAttribute('data-stream') : '';
        var playing = false;

        function setStatus(message) {
            if (status) {
                status.textContent = message;
            }
        }

        function setPlaying(state) {
            playing = state;
            playButton.classList.toggle('is-playing', state);
            playButton.setAttribute('aria-label', state ? 'Radio pauzeren' : 'Radio afspelen');
            playButton.innerHTML = state ? '<i class="fa fa-pause"></i>' : '<i class="fa fa-play"></i>';
            if (record) {
                record.classList.toggle('is-playing', state);
            }
            if (equalizer) {
                equalizer.classList.toggle('is-playing', state);
            }
        }

        playButton.addEventListener('click', function () {
            if (!audio || !stream) {
                setStatus('De radiostream is nog niet ingesteld.');
                return;
            }

            if (playing) {
                audio.pause();
                setPlaying(false);
                setStatus('Radio gepauzeerd');
                return;
            }

            if (!audio.src) {
                audio.src = stream;
            }

            setStatus('Verbinding maken met de live radio…');
            var result = audio.play();
            if (result && typeof result.then === 'function') {
                result.then(function () {
                    setPlaying(true);
                    setStatus('Nu live aan het afspelen');
                }).catch(function () {
                    setPlaying(false);
                    setStatus('Afspelen mislukt. Probeer het opnieuw.');
                });
            } else {
                setPlaying(true);
                setStatus('Nu live aan het afspelen');
            }
        });

        if (audio) {
            audio.volume = volume ? Number(volume.value) : 0.75;
            audio.addEventListener('playing', function () {
                setPlaying(true);
                setStatus('Nu live aan het afspelen');
            });
            audio.addEventListener('pause', function () {
                setPlaying(false);
            });
            audio.addEventListener('waiting', function () {
                setStatus('Live radio wordt geladen…');
            });
            audio.addEventListener('error', function () {
                setPlaying(false);
                setStatus('De radiostream kon niet worden geladen.');
            });
        }

        if (volume && audio) {
            volume.addEventListener('input', function () {
                audio.volume = Number(volume.value);
                if (audio.muted && audio.volume > 0) {
                    audio.muted = false;
                    updateMuteIcon();
                }
            });
        }

        function updateMuteIcon() {
            if (!muteButton || !audio) {
                return;
            }
            var muted = audio.muted || audio.volume === 0;
            muteButton.innerHTML = muted ? '<i class="fa fa-volume-off"></i>' : '<i class="fa fa-volume-up"></i>';
            muteButton.setAttribute('aria-label', muted ? 'Geluid inschakelen' : 'Geluid dempen');
        }

        if (muteButton && audio) {
            muteButton.addEventListener('click', function () {
                audio.muted = !audio.muted;
                updateMuteIcon();
            });
        }
    }

    function fallbackAvatar(event) {
        var image = event.currentTarget;
        image.removeEventListener('error', fallbackAvatar);
        image.src = 'default_images/avatar/default_avatar.png';
    }

    function userCard(user) {
        var card = document.createElement('article');
        card.className = 'dj-user-card' + (user.online ? ' is-online' : '');

        var avatarWrap = document.createElement('div');
        avatarWrap.className = 'dj-user-avatar-wrap';

        var image = document.createElement('img');
        image.className = 'dj-user-avatar';
        image.src = user.avatar || 'default_images/avatar/default_avatar.png';
        image.alt = user.name || 'Lid';
        image.loading = 'lazy';
        image.addEventListener('error', fallbackAvatar);

        var online = document.createElement('span');
        online.className = 'dj-user-online';
        online.setAttribute('aria-label', user.online ? 'Online' : 'Recent actief');

        avatarWrap.appendChild(image);
        avatarWrap.appendChild(online);

        var name = document.createElement('strong');
        name.className = 'dj-user-name';
        name.textContent = user.name || 'Lid';
        name.title = user.name || 'Lid';

        var state = document.createElement('small');
        state.className = 'dj-user-state';
        state.textContent = user.online ? 'Nu online' : 'Recent actief';

        card.appendChild(avatarWrap);
        card.appendChild(name);
        card.appendChild(state);
        return card;
    }

    function loadActiveUsers() {
        var holder = qs('#dj_active_users');
        var count = qs('#dj_online_count');
        if (!holder || !config.activeUsersUrl) {
            return;
        }

        fetch(config.activeUsersUrl, {
            credentials: 'same-origin',
            cache: 'no-store',
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        }).then(function (response) {
            if (!response.ok) {
                throw new Error('Request failed');
            }
            return response.json();
        }).then(function (data) {
            var users = Array.isArray(data.users) ? data.users : [];
            holder.innerHTML = '';

            if (count) {
                count.textContent = String(Number(data.online_count || 0));
            }

            if (!users.length) {
                var empty = document.createElement('div');
                empty.className = 'dj-empty-users';
                empty.textContent = 'Er zijn nog geen recent actieve leden om weer te geven.';
                holder.appendChild(empty);
                return;
            }

            users.forEach(function (user) {
                holder.appendChild(userCard(user));
            });
        }).catch(function () {
            holder.innerHTML = '<div class="dj-empty-users">De actieve leden konden niet worden geladen.</div>';
        });
    }

    function setupUserSlider() {
        var holder = qs('#dj_active_users');
        var previous = qs('#dj_users_prev');
        var next = qs('#dj_users_next');
        if (!holder) {
            return;
        }

        function move(direction) {
            holder.scrollBy({ left: direction * Math.max(230, holder.clientWidth * 0.72), behavior: 'smooth' });
        }

        if (previous) {
            previous.addEventListener('click', function () { move(-1); });
        }
        if (next) {
            next.addEventListener('click', function () { move(1); });
        }
    }

    var exactTranslations = {
        'Login': 'Inloggen',
        'Log in': 'Inloggen',
        'Sign in': 'Inloggen',
        'Register': 'Registreren',
        'Registration': 'Registreren',
        'Create account': 'Account aanmaken',
        'Recover': 'Herstellen',
        'Recovery': 'Wachtwoord herstellen',
        'Password recovery': 'Wachtwoord herstellen',
        'Forgot password?': 'Wachtwoord vergeten?',
        'Forgot password': 'Wachtwoord vergeten',
        'Username': 'Gebruikersnaam',
        'Username / Email': 'Gebruikersnaam / e-mailadres',
        'Username / E-mail': 'Gebruikersnaam / e-mailadres',
        'Password': 'Wachtwoord',
        'Email': 'E-mailadres',
        'E-mail': 'E-mailadres',
        'Male': 'Man',
        'Female': 'Vrouw',
        'Other': 'Anders',
        'Birthday': 'Geboortedatum',
        'Birth date': 'Geboortedatum',
        'Day': 'Dag',
        'Month': 'Maand',
        'Year': 'Jaar',
        'Guest login': 'Doorgaan als gast',
        'Guest': 'Gast',
        'Language': 'Taal',
        'Close': 'Sluiten',
        'Cancel': 'Annuleren',
        'Continue': 'Doorgaan',
        'Submit': 'Verzenden',
        'Send': 'Verzenden',
        'Reset': 'Herstellen',
        'Remember me': 'Onthoud mij',
        'Terms of use': 'Gebruiksvoorwaarden',
        'Privacy policy': 'Privacybeleid',
        'Rules': 'Regels',
        'Contact us': 'Contact',
        'Choose language': 'Kies een taal',
        'Select language': 'Kies een taal'
    };

    var placeholderTranslations = {
        'Username': 'Gebruikersnaam',
        'Username / Email': 'Gebruikersnaam / e-mailadres',
        'Username / E-mail': 'Gebruikersnaam / e-mailadres',
        'Password': 'Wachtwoord',
        'Email': 'E-mailadres',
        'E-mail': 'E-mailadres',
        'Enter username': 'Voer je gebruikersnaam in',
        'Enter password': 'Voer je wachtwoord in',
        'Enter email': 'Voer je e-mailadres in'
    };

    function translateTextNode(node) {
        if (node.nodeType !== 3) {
            return;
        }
        var raw = node.nodeValue;
        var trimmed = raw.trim();
        if (!trimmed || !Object.prototype.hasOwnProperty.call(exactTranslations, trimmed)) {
            return;
        }
        node.nodeValue = raw.replace(trimmed, exactTranslations[trimmed]);
    }

    function translateElement(element) {
        if (!element || element.nodeType !== 1) {
            return;
        }

        if (element.matches('input[placeholder], textarea[placeholder]')) {
            var placeholder = element.getAttribute('placeholder');
            if (placeholderTranslations[placeholder]) {
                element.setAttribute('placeholder', placeholderTranslations[placeholder]);
            }
        }

        if (element.matches('input[type="submit"], input[type="button"]')) {
            var value = element.value.trim();
            if (exactTranslations[value]) {
                element.value = exactTranslations[value];
            }
        }

        if (element.matches('[title]')) {
            var title = element.getAttribute('title');
            if (exactTranslations[title]) {
                element.setAttribute('title', exactTranslations[title]);
            }
        }

        Array.prototype.forEach.call(element.childNodes, translateTextNode);
        qsa('input[placeholder], textarea[placeholder], input[type="submit"], input[type="button"], option, label, button, a, h1, h2, h3, p, span, small', element).forEach(function (child) {
            if (child.matches('input[placeholder], textarea[placeholder]')) {
                var childPlaceholder = child.getAttribute('placeholder');
                if (placeholderTranslations[childPlaceholder]) {
                    child.setAttribute('placeholder', placeholderTranslations[childPlaceholder]);
                }
            }
            if (child.matches('input[type="submit"], input[type="button"]')) {
                var childValue = child.value.trim();
                if (exactTranslations[childValue]) {
                    child.value = exactTranslations[childValue];
                }
            }
            Array.prototype.forEach.call(child.childNodes, translateTextNode);
        });
    }

    function identifyModal(element) {
        if (!element || element.nodeType !== 1) {
            return;
        }

        var candidates = [];
        if (element.matches('.fancybox-content, .modal_box, .boom_box, .small_modal, .large_modal')) {
            candidates.push(element);
        }
        candidates = candidates.concat(qsa('.fancybox-content, .modal_box, .boom_box, .small_modal, .large_modal', element));

        candidates.forEach(function (modal) {
            modal.classList.add('dj-account-modal');
            var fieldCount = qsa('input:not([type="hidden"]), select', modal).length;
            var content = (modal.textContent || '').toLowerCase();
            if (fieldCount >= 4 || content.indexOf('geboortedatum') !== -1 || content.indexOf('register') !== -1) {
                modal.classList.add('dj-registration-modal');
            }
            translateElement(modal);
        });
    }

    function setupModalObserver() {
        identifyModal(document.body);
        var observer = new MutationObserver(function (mutations) {
            mutations.forEach(function (mutation) {
                Array.prototype.forEach.call(mutation.addedNodes, function (node) {
                    if (node.nodeType === 1) {
                        identifyModal(node);
                    }
                });
            });
        });
        observer.observe(document.body, { childList: true, subtree: true });
    }

    createParticles();
    setupPlayer();
    setupUserSlider();
    setupModalObserver();
    loadActiveUsers();

    var refresh = Number(config.refreshInterval || 30000);
    if (refresh >= 10000) {
        window.setInterval(loadActiveUsers, refresh);
    }
}());
