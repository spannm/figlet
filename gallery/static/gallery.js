(function () {
    'use strict';

    // scales card previews down so the whole rendering fits the card; all font sizes are
    // reset first, then measured, then set, so that the layout is computed only once
    function fitPreviews(all) {
        var pres = Array.prototype.filter.call(document.querySelectorAll('.card .fit'), function (pre) {
            if (all) {
                delete pre.dataset.fitted;
            }
            return !pre.dataset.fitted && !pre.parentElement.hidden;
        });
        pres.forEach(function (pre) {
            pre.style.fontSize = '10px';
        });
        var scales = pres.map(function (pre) {
            return Math.min(1, pre.clientWidth / pre.scrollWidth, 140 / pre.scrollHeight);
        });
        pres.forEach(function (pre, i) {
            pre.style.fontSize = Math.max(2, 10 * scales[i]).toFixed(2) + 'px';
            pre.dataset.fitted = 'true';
        });
    }

    // decodes the supported code points of a card, given as hexadecimal ranges such as "20-7e,c4"
    function supportedRanges(card) {
        if (!card.ranges) {
            card.ranges = (card.dataset.chars || '').split(',').filter(Boolean).map(function (range) {
                var bounds = range.split('-').map(function (hex) {
                    return parseInt(hex, 16);
                });
                return [bounds[0], bounds[bounds.length - 1]];
            });
        }
        return card.ranges;
    }

    function canRender(card, codepoints) {
        var ranges = supportedRanges(card);
        return codepoints.every(function (cp) {
            return ranges.some(function (range) {
                return cp >= range[0] && cp <= range[1];
            });
        });
    }

    var q = document.getElementById('q');
    if (q) {
        var pack = document.getElementById('pack');
        var type = document.getElementById('type');
        var text = document.getElementById('text');
        var count = document.getElementById('count');
        var empty = document.getElementById('empty');
        var cards = Array.prototype.slice.call(document.querySelectorAll('.card'));

        var filter = function () {
            var term = q.value.trim().toLowerCase();
            var codepoints = Array.from(new Set(Array.from(text.value, function (ch) {
                return ch.codePointAt(0);
            })));
            var shown = 0;
            cards.forEach(function (card) {
                var match = card.dataset.name.indexOf(term) >= 0
                    && (!pack.value || card.dataset.pack === pack.value)
                    && (!type.value || card.dataset.type === type.value)
                    && canRender(card, codepoints);
                card.hidden = !match;
                if (match) {
                    shown++;
                }
            });
            count.textContent = shown + ' of ' + cards.length + ' fonts';
            empty.hidden = shown > 0;
            fitPreviews(false);
        };

        var params = new URLSearchParams(location.search);
        q.value = params.get('q') || '';
        text.value = params.get('text') || '';
        [q, pack, type, text].forEach(function (el) {
            el.addEventListener('input', filter);
        });
        filter();
    } else {
        fitPreviews(false);
    }

    var timer;
    window.addEventListener('resize', function () {
        clearTimeout(timer);
        timer = setTimeout(function () {
            fitPreviews(true);
        }, 150);
    });

    document.querySelectorAll('pre.code').forEach(function (pre) {
        if (!navigator.clipboard) {
            return;
        }
        var button = document.createElement('button');
        button.type = 'button';
        button.className = 'copy';
        button.textContent = 'Copy';
        button.addEventListener('click', function () {
            navigator.clipboard.writeText(pre.querySelector('code').textContent).then(function () {
                button.textContent = 'Copied';
                setTimeout(function () {
                    button.textContent = 'Copy';
                }, 1500);
            });
        });
        pre.appendChild(button);
    });
})();
