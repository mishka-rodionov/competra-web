package com.competra.web.utils

@JsFun(
    "(url) => { if (url.startsWith('tel:') || url.startsWith('mailto:')) { window.location.href = url; } " +
        "else { window.open(url, '_blank', 'noopener,noreferrer'); } }"
)
external fun openExternalLink(url: String)
