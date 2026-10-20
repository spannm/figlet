(function () {
    'use strict';

    // scales each card preview down so the whole rendering fits the card
    function fitPreviews() {
        document.querySelectorAll('.card .fit').forEach(function (pre) {
            if (pre.offsetParent === null) {
                return;
            }
            pre.style.fontSize = '10px';
            var scale = Math.min(1, pre.clientWidth / pre.scrollWidth, 140 / pre.scrollHeight);
            pre.style.fontSize = Math.max(2, 10 * scale).toFixed(2) + 'px';
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
            fitPreviews();
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
            timer = setTimeout(fitPreviews, 150);
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
