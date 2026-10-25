#!/usr/bin/env python3
"""Applies data/vinmonopolet/match_analysis_2026-10-25.csv to data/vinmonopolet/vote_matches.csv.

The analysis file holds my reading of the machine matches (one row per entry, a reason and a confidence H/M/L):
  match          -> status 'likely', method 'analysis-high' or 'analysis-medium', the product named in the file
  not-a-product  -> status 'not-a-product', method 'analysis'
  none           -> status 'none', method 'analysis'
  leave          -> untouched, except that a machine 'likely' row is demoted to 'review' (method 'analysis-weak')
Only confidence H and M are applied for the first three. Human decisions live in the database and are never touched
here. Idempotent: a second run changes nothing. Standard library only; run from the repository root:
    python3 -I tools/apply_match_analysis.py
Rebuilding vote_matches.csv with tools/match_votes_to_products.py starts from the machine's guesses again: run this after it.
"""
import csv
import sys
from collections import defaultdict

DIR = 'data/vinmonopolet/'
ANALYSIS = DIR + 'match_analysis_2026-10-25.csv'


def read(path):
    with open(path, encoding='utf-8', newline='') as f:
        r = csv.DictReader(f)
        return r.fieldnames, list(r)


def main():
    fields, votes = read(DIR + 'vote_matches.csv')
    _, analysis = read(ANALYSIS)
    _, products = read(DIR + 'products.csv')
    by_name = defaultdict(set)
    for p in products:
        for n in [p['name']] + [x for x in (p.get('names_seen') or '').split('|') if x.strip()]:
            by_name[n.strip().lower()].add(p['article_no'])
    index = {(v['year'], v['vote_beer']): v for v in votes}
    changed = 0
    counts = defaultdict(int)
    for a in analysis:
        v = index.get((a['year'], a['vote_beer']))
        if v is None:
            sys.exit('no vote_matches row for %s / %s' % (a['year'], a['vote_beer']))
        action, conf = a['proposed'], a['confidence']
        new = dict(v)
        if action == 'match' and conf in ('H', 'M'):
            arts = sorted(by_name.get(a['proposed_product'].lower(), set()))
            new.update(status='likely', method='analysis-high' if conf == 'H' else 'analysis-medium', score='',
                       product_article_no=arts[0] if len(arts) == 1 else '',
                       article_options=';'.join(arts) if len(arts) > 1 else '',
                       product_name=a['proposed_product'], list_year='')
        elif action in ('not-a-product', 'none') and conf in ('H', 'M'):
            new.update(status=action, method='analysis', score='', product_article_no='', article_options='',
                       product_name='', list_year='')
        elif action == 'leave' and v['status'] == 'likely':
            new.update(status='review', method='analysis-weak')
        else:
            continue
        counts[new['status'] + '/' + new['method']] += 1
        if new != v:
            v.update(new)
            changed += 1
    with open(DIR + 'vote_matches.csv', 'w', encoding='utf-8', newline='') as f:
        w = csv.DictWriter(f, fieldnames=fields, lineterminator='\n')
        w.writeheader()
        w.writerows(votes)
    print('rows changed: %d; applied: %s' % (changed, dict(counts)))


if __name__ == '__main__':
    main()
