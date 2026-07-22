DJIZZEL RADIO — CODYCHAT V11 DUTCH PROFESSIONAL LOGIN V5
=======================================================

INSTALLATIE
1. Pak deze ZIP uit.
2. Upload de map "DjizzelRadio" naar:
   /control/login/DjizzelRadio/
3. Overschrijf de bestaande bestanden.
4. Laat het bestaande bestand hieronder staan wanneer je de eerder aangeleverde
   Djizzel-logoafbeelding wilt blijven gebruiken:
   /control/login/DjizzelRadio/assets/djizzel-radio-logo.png
5. Selecteer DjizzelRadio als login-template in CodyChat.
6. Wis daarna CodyChat-, Cloudflare- en browsercache en laad met Ctrl + F5.

BELANGRIJKE CODYCHAT-INTEGRATIE
- De accountknoppen gebruiken CodyChat V11 native .boom_request-routes:
  box/login
  box/registration
  box/guest_login
  box/language
- Bridge-login blijft ondersteund via bridgeMode() en bridgeLogin(getChatPath()).
- Registratie en gasttoegang worden alleen getoond wanneer CodyChat deze toestaat.
- Juridische footerlinks gebruiken de bestaande CodyChat-pagina’s:
  terms.php, privacy.php, rules.php en contact_us.php.
- De hero gebruikt automatisch $setting['title'] en $setting['description'].
- De radioplayer gebruikt automatisch $player['stream_url'] en
  $player['stream_alias'].

LOGO
De ZIP bevat een nette SVG-noodvariant. Wanneer de bestaande PNG aanwezig is,
gebruikt de template automatisch die originele Djizzel Radio PNG.

BESTANDEN
- login.php
- login.css
- fetch_active_users.php
- assets/djizzel-login.css
- assets/djizzel-login.js
- assets/djizzel-radio-logo.svg
- CHANGELOG.txt
