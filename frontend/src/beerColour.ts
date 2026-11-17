/**
 * The colour a beer has in the glass, guessed from its style and name: stouts near black, dark Christmas beers
 * brown, pale ones gold, and the classic juleøl copper red when nothing else says otherwise.
 */
export function beerColour(beer: { name?: string | null; style?: string | null } | null | undefined): string {
  const text = `${beer?.style ?? ''} ${beer?.name ?? ''}`.toLowerCase()
  if (/stout|porter|imperial|svart|black/.test(text)) return '#2a1710'
  if (/mørk|dark|dunkel|bock|bokk|doppel|brown|brun|quad|barley|dubbel|strong|sterk/.test(text)) return '#5a2c14'
  if (/ipa|pale|lys|pils|blond|wit|weiss|hvete|tripel|saison|gose|sour|sur/.test(text)) return '#d9982b'
  return '#8a3b16'
}
