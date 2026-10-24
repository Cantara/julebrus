import { ref } from 'vue'

// The app copy is Norwegian bokmål. The consent screen, the sponsor line and the
// claim-your-history section can also be shown in English; this is the whole
// mechanism, a lookup keyed by the language toggle (no library).
export type Lang = 'nb' | 'en'

function stored(): Lang {
  try {
    return localStorage.getItem('lang') === 'en' ? 'en' : 'nb'
  } catch {
    return 'nb'
  }
}

export const lang = ref<Lang>(stored())

export function setLang(l: Lang) {
  lang.value = l
  try {
    localStorage.setItem('lang', l)
  } catch {
    /* private mode: the choice just is not remembered */
  }
}

const dict = {
  nb: {
    sponsoredBy: 'Støttet av',
    consentTitle: 'Før du begynner',
    consentAccept: 'Jeg samtykker',
    consentVersion: 'Versjon',
    consentAlready: 'Du har samtykket.',
    consentContinue: 'Fortsett til appen',
    consentFailed: 'Kunne ikke lagre samtykket. Prøv igjen.',
    withdrawTitle: 'Samtykke',
    withdrawInfo: 'Du kan trekke samtykket tilbake. Da kan du ikke bruke appen før du samtykker på nytt.',
    withdrawButton: 'Trekk tilbake samtykke',
    withdrawConfirm: 'Er du sikker? Du blir sendt til samtykkesiden.',
    withdrawYes: 'Ja, trekk tilbake',
    cancel: 'Avbryt',
    claimTitle: 'Finn dine tidligere scorer',
    claimIntro: 'Tidligere år brukte vi bare fornavn, og noen er slått sammen til én person. Velg personene som er deg, så sjekker en administrator koblingen. Ingenting kobles automatisk.',
    claimSuggested: 'Forslag ut fra navnet ditt',
    claimOthers: 'Alle navn',
    claimShowAll: 'Vis alle navn',
    claimHideAll: 'Skjul alle navn',
    claimButton: 'Dette er meg',
    claimTaken: 'Allerede koblet',
    claimLinked: 'Allerede koblet til en innlogging',
    claimLinkedToYou: 'Koblet til din innlogging',
    claimWithdraw: 'Trekk tilbake',
    claimLimit: 'Du kan ha høyst 5 forespørsler til behandling om gangen.',
    claimExists: 'Du har allerede bedt om det navnet.',
    claimTakenErr: 'Det navnet er allerede koblet til noen andre.',
    claimError: 'Noe gikk galt. Prøv igjen.',
    claimYears: 'år',
    claimRatings: 'scorer',
    claimMine: 'Dine forespørsler',
    claimNone: 'Ingen navn funnet.',
    PENDING: 'Venter på godkjenning',
    APPROVED: 'Godkjent',
    REJECTED: 'Avvist',
    adminTitle: 'Admin: forespørsler om historikk',
    adminNone: 'Ingen ventende forespørsler.',
    approve: 'Godkjenn',
    reject: 'Avvis',
  },
  en: {
    sponsoredBy: 'Sponsored by',
    consentTitle: 'Before you start',
    consentAccept: 'I accept',
    consentVersion: 'Version',
    consentAlready: 'You have given your consent.',
    consentContinue: 'Continue to the app',
    consentFailed: 'Could not save your consent. Please try again.',
    withdrawTitle: 'Consent',
    withdrawInfo: 'You can withdraw your consent. You then cannot use the app until you consent again.',
    withdrawButton: 'Withdraw consent',
    withdrawConfirm: 'Are you sure? You will be sent to the consent page.',
    withdrawYes: 'Yes, withdraw',
    cancel: 'Cancel',
    claimTitle: 'Find your earlier ratings',
    claimIntro: 'In earlier years we only used first names, and some are joined into one person. Pick the people who are you and an administrator will check the link. Nothing is linked automatically.',
    claimSuggested: 'Suggestions based on your name',
    claimOthers: 'All names',
    claimShowAll: 'Show all names',
    claimHideAll: 'Hide all names',
    claimButton: 'This is me',
    claimTaken: 'Already linked',
    claimLinked: 'Already linked to a login',
    claimLinkedToYou: 'Linked to your login',
    claimWithdraw: 'Withdraw',
    claimLimit: 'You can have at most 5 requests waiting at a time.',
    claimExists: 'You have already asked for that name.',
    claimTakenErr: 'That name is already linked to someone else.',
    claimError: 'Something went wrong. Please try again.',
    claimYears: 'years',
    claimRatings: 'ratings',
    claimMine: 'Your requests',
    claimNone: 'No names found.',
    PENDING: 'Waiting for approval',
    APPROVED: 'Approved',
    REJECTED: 'Rejected',
    adminTitle: 'Admin: history requests',
    adminNone: 'No pending requests.',
    approve: 'Approve',
    reject: 'Reject',
  },
} as const

export type Key = keyof (typeof dict)['nb']

export function t(key: Key): string {
  return dict[lang.value][key]
}
