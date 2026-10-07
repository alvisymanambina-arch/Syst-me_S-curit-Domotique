# Système de sécurité domotique

> Système de sécurité — Microcontrôleur avancé

> Projet de système de surveillance et de contrôle d'éclairage, piloté par Bluetooth depuis une application Android.

Le système associe un microcontrôleur **PIC18F4550**, des capteurs de mouvement et de luminosité, un module Bluetooth classique HC-05/HC-06, ainsi qu'une application Android de contrôle et de suivi.

## Fonctionnalités

- Contrôle manuel des éclairages du salon, de la cour et des toilettes.
- Détection de mouvement dans la cour et les toilettes à l'aide de capteurs PIR.
- Gestion de l'éclairage selon la luminosité ambiante (LDR).
- Alerte sonore lors d'un mouvement détecté dans la cour.
- Communication Bluetooth série à 9 600 bauds avec un module HC-05/HC-06.
- Affichage de l'état des zones dans l'application Android.
- Historique horodaté des détections de mouvement, avec notifications Android.
- Réglage de l'horloge DS3231 par Bluetooth.

## Architecture

```text
[ Application Android ] <-- Bluetooth série --> [ HC-05 / HC-06 ] <-- UART --> [ PIC18F4550 ]
                                                                                  |-- LDR
                                                                                  |-- PIR cour / toilettes
                                                                                  |-- Éclairages
                                                                                  |-- Buzzer
                                                                                  `-- RTC DS3231
```

## Contenu du dépôt

| Dossier / fichier | Description |
| --- | --- |
| `systeme_securite_PIC/` | Firmware C pour le PIC18F4550, projet CCS C Compiler. |
| `SystemSecurite/` | Application Android Studio (Java) de contrôle Bluetooth. |
| `remarque.txt` | Instructions d'appairage Bluetooth et de réglage de la date. |

## Matériel utilisé

- PIC18F4550 (oscillateur 20 MHz)
- Module Bluetooth HC-05 ou HC-06
- Module RTC DS3231 (I²C)
- 2 capteurs PIR : cour et toilettes
- Capteur de luminosité LDR
- 3 sorties d'éclairage : salon, cour et toilettes
- Buzzer
- Smartphone Android avec Bluetooth classique

## Connexions principales

| Élément | Broche PIC |
| --- | --- |
| Bluetooth TX / RX | C6 (TX), C7 (RX) |
| Buzzer | C0 |
| Éclairage cour (PWM) | C2 |
| Éclairage salon | E0 |
| Éclairage toilettes | E1 |
| PIR toilettes | A1 |
| PIR cour | A3 |
| LDR | A0 |
| DS3231 SCL / SDA | D0 / D1 |

## Commandes Bluetooth

> Chaque commande doit se terminer par un saut de ligne (`\n` ou `\r`).

| Commande | Action |
| --- | --- |
| `SALON_ON` / `SALON_OFF` | Allume / éteint l'éclairage du salon. |
| `COUR_ON` / `COUR_OFF` | Active / désactive le mode manuel de la cour. |
| `TOILETTE_ON` / `TOILETTE_OFF` | Active / désactive le mode manuel des toilettes. |
| `LDR` | Demande la valeur du capteur de luminosité. |
| `PING` | Vérifie la communication ; le PIC répond `PONG`. |
| `ETAT` | Demande une synchronisation des états. |
| `SETDATE=AAMMJJhhmmssJ` | Règle la date et l'heure du DS3231. |

Exemple : `SETDATE=2608231415001`.

## Démarrage

### 1. Firmware PIC

1. Ouvrir `systeme_securite_PIC/systeme_securite_PIC.c` dans CCS C Compiler.
2. Compiler le firmware pour le PIC18F4550.
3. Programmer le microcontrôleur.
4. Vérifier le câblage des capteurs, sorties, module Bluetooth et DS3231.

Au démarrage, le PIC envoie `SYSTEME_PRET` sur le Bluetooth.

### 2. Application Android

1. Ouvrir le dossier `SystemSecurite/` dans Android Studio.
2. Laisser Gradle synchroniser le projet.
3. Construire et installer l'application sur un appareil Android (minSdk 24, targetSdk 36).
4. Autoriser les permissions Bluetooth et notifications si Android les demande.
5. Appairer le téléphone avec le HC-05/HC-06, puis le sélectionner dans l'application.

## Notes Bluetooth

- Le code d'appairage par défaut d'un HC-05/HC-06 est souvent `1234` ou `0000`.
- Pour les tests, l'application **Serial Bluetooth Terminal** peut être utilisée pour envoyer les commandes directement.
- Les modules HC-05/HC-06 utilisent le Bluetooth classique ; ils ne sont généralement pas compatibles directement avec les applications BLE sur iPhone.
