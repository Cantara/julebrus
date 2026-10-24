<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { get } from '../api/client'
import type { HistoricSeason } from '../api/types'
import LangToggle from '../components/LangToggle.vue'
import Snow from '../components/Snow.vue'
import { lang } from '../i18n'
import { useMe } from '../stores/me'

/**
 * The story: why Cantara tastes Christmas beer, what went wrong, and how the
 * season works now. Long prose lives here rather than in the i18n dictionary.
 */
const store = useMe()
const seasons = ref<HistoricSeason[]>([])
onMounted(async () => {
  seasons.value = await get<HistoricSeason[]>('/api/history/seasons').catch(() => [])
})
const firstYear = computed(() => (seasons.value.length ? Math.min(...seasons.value.map((s) => s.year)) : 1998))
const year = computed(() => store.me?.season.year ?? new Date().getFullYear())

type Section = { title: string; paragraphs: string[] }
const copy = computed<{ title: string; lead: string; sections: Section[]; closing: string; cta: string }>(() =>
  lang.value === 'en'
    ? {
        title: 'The story of the Christmas beer tasting',
        lead: `Every year since ${firstYear.value}, give or take a few we lost to the wiki, people at Cantara have sat down with the season's Christmas beers, a stack of dice scores and far too many opinions. This is why we still do it, and how it works now.`,
        sections: [
          {
            title: 'How it started',
            paragraphs: [
              'Norwegian breweries release their juleøl in November, and Vinmonopolet fills a whole shelf with them. Someone at Cantara had the obvious idea: buy one of each, gather the team, and settle once and for all which one is best. Scores on a dice, one to six. Winner announced. Arguments for the rest of the year.',
              `The record goes back to ${firstYear.value}. ${seasons.value.length || 'Twelve'} seasons of votes survive, with names, weights and all. You can find your own old scores on your profile, if you were there.`,
            ],
          },
          {
            title: 'What went wrong',
            paragraphs: [
              'The shelf kept growing. Forty bottles, then fifty, then more. One evening, one table, everyone gets a splash of each. By bottle fifteen the palate is gone, by bottle thirty the scores are noise, and the beers that were opened last never stood a chance.',
              'So the tasting that was supposed to find the best beer was mostly finding out who could still hold a pen.',
            ],
          },
          {
            title: 'The season',
            paragraphs: [
              `Now the tasting lasts the whole season: 1 November to 23 December. Three rules keep it honest. At most five beers a day, so every pour is a real pour. At most five solo days, so most of it happens in company. And one score per beer per person, ever.`,
              'The home screen is an advent calendar. Every day you taste opens a door. Tap it later and see what was on the table.',
            ],
          },
          {
            title: 'An evening around the table',
            paragraphs: [
              'Someone hosts. Their phone shows a QR code, the rest scan it, and you are a group. The host puts a beer on the table, everyone rates it on their own phone, and nobody sees anything until the last person has scored. Then the wrapping tears off on every phone at once, and the dice land together. No anchoring on whoever shouts first.',
              'After two beers you get the duel: which was better? Those answers build a second ranking on the side that does not care how generous or stingy you are with the dice. The dice decide the winner. The duels tell you who was right.',
            ],
          },
          {
            title: 'Probies and veterans',
            paragraphs: [
              'A tradition from the earliest years: your first season, your scores count half. Taste at least seven beers and behave, and next year you count in full. Give sixes to everything from your home town and ones to the rest, and the year after your scores are recorded but do not move the averages. You can still taste, still vote, still see everything. You just do not steer.',
            ],
          },
          {
            title: 'Badges, and the finale',
            paragraphs: [
              'Along the way you collect baubles on a tree: first tasting, seven days in a row, the beer nobody else had tried, ten different people at your table, the whole of December without a day off. They hang on your profile.',
              `On 23 December at six in the evening the star lights up. Cantara's Christmas beer ${year.value} is named, the podium goes up, the duels get their say, and the season's awards are handed out: the most contrary palate, the kindest dice, the best guesser. Then we argue about it until November.`,
            ],
          },
        ],
        closing: 'Skål, and welcome to the table.',
        cta: 'To the calendar',
      }
    : {
        title: 'Historien om juleølsmakingen',
        lead: `Hvert år siden ${firstYear.value}, pluss minus noen år wikien har glemt, har folk i Cantara satt seg ned med sesongens juleøl, en bunke terningkast og altfor mange meninger. Dette er hvorfor vi fortsatt gjør det, og hvordan det fungerer nå.`,
        sections: [
          {
            title: 'Slik begynte det',
            paragraphs: [
              'Bryggeriene slipper juleølet i november, og Polet fyller en hel hylle. Noen i Cantara fikk den åpenbare ideen: kjøp én av hver, samle gjengen, og avgjør én gang for alle hvilket som er best. Terningkast fra én til seks. Vinner kåres. Krangling resten av året.',
              `Arkivet går tilbake til ${firstYear.value}. ${seasons.value.length || 'Tolv'} sesonger med stemmer har overlevd, med navn og vekt og alt. Du finner dine egne gamle terningkast på profilen din, om du var med.`,
            ],
          },
          {
            title: 'Det som gikk galt',
            paragraphs: [
              'Hylla ble lengre. Førti flasker, så femti, så flere. Én kveld, ett bord, alle får en skvett av hver. Ved flaske femten er ganen borte, ved flaske tretti er terningkastene støy, og ølene som ble åpnet sist hadde aldri en sjanse.',
              'Så smakingen som skulle finne det beste ølet, fant stort sett ut hvem som fortsatt klarte å holde en penn.',
            ],
          },
          {
            title: 'Sesongen',
            paragraphs: [
              'Nå varer smakingen hele sesongen: 1. november til 23. desember. Tre regler holder det ærlig. Maks fem øl om dagen, så hver skjenk er en skikkelig skjenk. Maks fem solo-dager, så det meste skjer i selskap. Og ett terningkast per øl per person, for alltid.',
              'Hjemskjermen er en adventskalender. Hver dag du smaker, åpnes en luke. Trykk på den senere og se hva som sto på bordet.',
            ],
          },
          {
            title: 'En kveld rundt bordet',
            paragraphs: [
              'Noen er vert. Telefonen deres viser en QR-kode, resten skanner, og vips er dere en gruppe. Verten setter et øl på bordet, alle kaster terning på egen telefon, og ingen ser noe før den siste har svart. Da rives papiret av på alle telefonene samtidig, og terningene lander sammen. Ingen lar seg påvirke av den som roper først.',
              'Etter to øl kommer duellen: hvilket var best? Svarene bygger en sideranking som ikke bryr seg om hvor snill eller streng du er med terningen. Terningen kårer vinneren. Duellene sier hvem som hadde rett.',
            ],
          },
          {
            title: 'Probies og veteraner',
            paragraphs: [
              'En tradisjon fra de første årene: første sesong teller terningkastene dine halvt. Smak minst sju øl og oppfør deg, så teller du fullt neste år. Gi seksere til alt fra hjembyen og enere til resten, og året etter blir terningkastene dine notert, men flytter ikke snittet. Du kan fortsatt smake, fortsatt stemme, fortsatt se alt. Du styrer bare ikke.',
            ],
          },
          {
            title: 'Merker, og finalen',
            paragraphs: [
              'Underveis samler du kuler på et tre: første smaking, sju dager på rad, ølet ingen andre hadde prøvd, ti forskjellige folk rundt bordet ditt, hele desember uten en fridag. De henger på profilen din.',
              `23. desember klokka seks tennes stjernen. Cantaras juleøl ${year.value} kåres, pallen settes opp, duellene får si sitt, og årets priser deles ut: den mest kontrære ganen, den snilleste terningen, den beste gjetteren. Så krangler vi om det til november.`,
            ],
          },
        ],
        closing: 'Skål, og velkommen til bords.',
        cta: 'Til kalenderen',
      },
)
</script>

<template>
  <Snow :enabled="true" :count="20" />
  <div class="row" style="align-items: baseline; margin-bottom: 4px">
    <h1 style="margin: 8px 0">{{ copy.title }}</h1>
    <LangToggle />
  </div>
  <p class="lead">{{ copy.lead }}</p>

  <section v-for="s in copy.sections" :key="s.title" class="card story">
    <h2>{{ s.title }}</h2>
    <p v-for="(p, i) in s.paragraphs" :key="i">{{ p }}</p>
  </section>

  <p class="display closing">{{ copy.closing }}</p>
  <router-link class="btn" to="/" style="display: block; text-align: center">{{ copy.cta }}</router-link>
</template>
