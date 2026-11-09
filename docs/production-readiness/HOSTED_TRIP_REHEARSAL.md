# Hosted development trip rehearsal

Status: **BLOCKED — not executed on the current hosted schema**.

Local database and API rehearsals cover booking, competing rider acceptance, arrival, PIN, start, completion, fare protection, cancellation and cash acknowledgement. The service-fee migration adds exact-once completion charges and verified-payment credits, but it is not hosted.

A valid hosted rehearsal requires the backup/restore gate, both migrations, reviewed Functions, separate synthetic Passenger/Rider/Admin identities and safe fixture cleanup. It must observe the same trip across both apps, Admin and database through booking, acceptance race, wrong/correct PIN, completion, cash and service-fee verification. No local fixture result is presented as hosted evidence.
