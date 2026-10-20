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

    var q = document.getElementById('q');
    if (q) {
        var pack = document.getElementById('pack');
        var type = document.getElementById('type');
        var count = document.getElementById('count');
        var empty = document.getElementById('empty');
        var cards = Array.prototype.slice.call(document.querySelectorAll('.card'));

        var filter = function () {
            var term = q.value.trim().toLowerCase();
            var shown = 0;
            cards.forEach(function (card) {
                var match = card.dataset.name.indexOf(term) >= 0
                    && (!pack.value || card.dataset.pack === pack.value)
                    && (!type.value || card.dataset.type === type.value);
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
        [q, pack, type].forEach(function (el) {
            el.addEventListener('input', filter);
        });
        filter();

        var timer;
        window.addEventListener('resize', function () {
            clearTimeout(timer);
            timer = setTimeout(function () {
                fitPreviews(true);
            }, 150);
        });
    }

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
