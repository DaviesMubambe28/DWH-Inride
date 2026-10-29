# DHW-inRide Privacy Policy

**Effective date:** 24 October 2026  
**Last updated:** 24 October 2026

> **Publication fields to complete:** Replace every bracketed field before release. This policy is a product-specific draft for review by a Zambian legal practitioner and the Data Protection Commissioner (DPC) compliance adviser. It does not establish that DHW-inRide or any vehicle category is licensed to operate.

## 1. Who is responsible for your information

“DHW-inRide”, “we”, “us” and “our” mean **[full legal name of operating entity]**, a **[company/other legal form]** registered in Zambia under number **[PACRA number]**, with its principal address at **[physical address, Chongwe, Zambia]**. We determine why and how personal data is processed and act as the data controller for the service.

Contact our privacy team or Data Protection Officer at **[privacy email]**, **[telephone]**, or **[postal address]**. DPO details: **[name/title and contact details]**. Do not publish this policy until the operator, service contact and DPO details have been confirmed.

This policy applies to the DHW-inRide passenger and rider applications, the operations dashboard, and related support. It should be read with the Terms of Service.

## 2. Information we collect

Depending on how you use the service, we may process:

- **Account and contact data:** name, phone number, email address, account ID, account role and authentication/session information.
- **Ride data:** pickup and destination addresses and coordinates, route distance and duration, fare estimate and fare, payment method, ride status, timestamps, assigned rider/passenger, trip PIN verification records, and complaints or support correspondence.
- **Location data:** pickup and destination coordinates, foreground location used to select a pickup, and rider positions shared while available for pilot dispatch or assigned to an active trip. The rider app requests location permission before going online. The server uses a fresh rider position to limit dispatch to the approved service area. Active-trip positions are stored in the trip-location table and are visible only to authorized participants and operations users under database policies. The latest dispatch-availability position is kept separately and expires for dispatch purposes after two minutes. This release does not request Android background-location permission. The approved retention schedule for historical trip locations must be completed below before release.
- **Rider eligibility and verification data:** identity and contact details, rider licence, vehicle registration, road-service or other transport permits, police clearance, rider-document images and verification decisions. We request and use only the documents needed to assess eligibility and meet applicable obligations.
- **Payment acknowledgement data:** chosen payment method and in-app passenger/rider acknowledgements. For external mobile money, we do not receive transaction credentials or confirmation directly from a mobile-money provider in the current app. An in-app acknowledgement records what a participant reports; it is not independent proof of settlement.
- **Device and service data:** app version, device and diagnostic information, security events, access logs, and information needed to investigate errors or abuse. The precise fields depend on the app and hosting configuration.

Please do not submit another person’s information or a rider document unless you are authorised to do so.

## 3. Why we use information and our legal grounds

We use personal data for these purposes:

| Purpose | Examples | Legal ground, as applicable |
|---|---|---|
| Create and secure accounts | Sign-up, login, role and account recovery | Steps requested by you to provide the service; legal obligations; legitimate security interests |
| Arrange and administer rides | Match passenger and rider, show route details, calculate and display fares, manage status and trip PIN | Steps requested by you and performance of the ride arrangement; legitimate interests in safe and reliable operations |
| Verify riders and vehicles | Review rider identity, licence, police clearance and vehicle/transport documents; approve or suspend access | Legal obligations and legitimate interests in preventing unsafe or unlawful rides |
| Support, safety and disputes | Investigate complaints, incidents, fraud, abuse, fare disputes and account security | Legal obligations and legitimate interests in protecting users and our service |
| Payments and reconciliation | Display the fare and payment method; record participant acknowledgements | Steps requested by you, accounting obligations and legitimate interests in resolving payment disputes |
| Improve and maintain the service | Diagnose faults, protect systems and evaluate service performance | Legitimate interests, subject to applicable law and your rights |
| Meet legal requests | Respond to lawful regulatory, court or law-enforcement demands | Compliance with legal obligations |

Where we rely on consent, you may withdraw it at any time. Withdrawal does not make earlier processing unlawful and may prevent a feature, such as current-location selection, from working. We will not use personal data for a materially different purpose without a lawful basis and, where required, notice or consent.

## 4. Location permissions and controls

You can enter a pickup without granting device-location permission if the app offers another way to select it. When you choose **Use current location**, the app requests or uses foreground device location to identify a pickup. The trip’s pickup and destination coordinates are then used to arrange the ride, calculate a route and fare, show trip information, and resolve disputes or safety incidents.

The current release does not request continuous background location or persist live rider GPS pings. A map animation or “Live tracking” screen should not be understood as proof that continuous GPS tracking is operating. If we introduce background or continuous location collection, we will first update this policy, provide a clear in-app notice, request the required Android permission and consent, limit access to the active trip, and explain the retention period before enabling it.

## 5. Who receives personal data

We disclose information only as needed for the purposes above, including to:

- **The trip participant:** Before acceptance, a rider may receive route and pickup/drop-off coordinates needed to decide whether to accept. After assignment, the passenger and assigned rider receive the trip and contact details needed to complete it. We do not make passenger personal details available to unassigned riders.
- **Our authorised operations staff:** Admins may access profiles, trip records and rider-verification documents for support, safety, regulatory, finance and fraud-prevention duties. Access should be restricted by role and logged.
- **Service providers:** Supabase provides database, authentication, storage and realtime infrastructure; Mapbox receives location or search coordinates when its maps, geocoding or routing functions are used. **[List any additional providers actually enabled before launch.]** Providers must be bound by appropriate confidentiality, security and data-processing terms.
- **Authorities and advisers:** Regulators, courts, law enforcement, insurers, lawyers or auditors where disclosure is required by law or reasonably necessary to protect rights, safety or legal claims.
- **A business successor:** If the operating entity undergoes a merger, restructuring or asset transfer, subject to applicable law and appropriate safeguards.

We do not sell personal data. We do not share precise location with advertising networks for behavioural advertising.

## 6. Cross-border processing

Our hosting, mapping or other providers may process data in countries outside Zambia. Before a transfer, we will assess the destination and put in place safeguards required by Zambia’s Data Protection Act, 2021, including an appropriate contractual or other lawful transfer mechanism. **[Identify hosting and provider processing regions and the transfer safeguard before launch.]**

## 7. Retention and deletion

We keep personal data only for a defined purpose, only while it remains relevant to that purpose, and in accordance with applicable legal retention requirements. Section 51 of Zambia’s Data Protection Act, 2021 requires personal information to be retained for the relevant purpose and for at least one year thereafter, unless another prescribed period applies. We will not promise deletion sooner where that minimum or another legal hold applies.

Our intended schedule is:

| Data | Intended retention |
|---|---|
| Account and profile data | While the account is active and for at least one year after the relevant account purpose ends; longer only where needed for a legal obligation, unresolved dispute, safety investigation or legal claim. |
| Ride record, fare and payment acknowledgements | At least one year after the ride purpose ends, and longer where tax, accounting, transport, consumer-protection or dispute requirements apply. |
| Pickup/drop-off coordinates and route details | Stored with the ride record and retained for at least one year after the ride purpose ends; then deleted or irreversibly anonymised unless a legal hold or continuing lawful purpose applies. |
| Rider verification documents | While the rider is being assessed or active and for at least one year after the verification purpose ends; longer only where required for licensing, safety, a complaint, an investigation or legal claim. Access is restricted. |
| Security and diagnostic logs | For the documented security or diagnostic purpose and applicable legal period; **[set and publish a specific maximum log-retention period before launch]**. |
| Continuous rider GPS history | Not collected or persisted by the current release. If enabled, a specific retention period and purpose will be published before collection begins. |

We keep a record of processing purposes and disclosures as required by law. When a record is no longer needed, we securely delete it or irreversibly anonymise it. Backups may retain deleted data for a limited rotation period, **[state the actual backup purge period]**, and are not used for ordinary operations.

## 8. Security

We use access controls, role-based permissions, secure authentication, protected storage and other technical and organisational measures appropriate to the risks. No system can be guaranteed completely secure. You must protect your password and trip PIN, keep your device secure, and tell us promptly if you suspect unauthorised account access. We will handle and report a personal-data breach in accordance with applicable law, including statutory notification obligations.

## 9. Your rights and complaints

Subject to applicable law and its exceptions, you may ask us to:

- confirm whether we process your personal data and provide access to it and information about its use;
- correct inaccurate or incomplete data;
- erase data where a legal ground for erasure applies;
- object to or request restriction of certain processing;
- withdraw consent where processing is based on consent; and
- explain relevant recipients, retention periods and safeguards for cross-border processing.

Contact **[privacy email]** with your request and enough information for us to verify your identity. We may ask for additional verification and may retain information we are legally required to keep. If you are not satisfied, you may complain to Zambia’s Data Protection Commissioner: **[insert current DPC complaint channel and address after verification]**.

## 10. Children

DHW-inRide is not designed for children to create accounts or independently book rides. Do not create an account for a child or submit a child’s information without the authority and any parental or guardian involvement required by law. If we discover that a child’s data was collected unlawfully, contact us so we can assess and address it.

## 11. Changes to this policy

We will post the current policy in the app or at **[policy URL]** and update the “Last updated” date. For a material change, we will provide notice before the change takes effect and obtain consent where required. Continued use does not override a consent requirement.

## 12. Zambia legal references

This policy is intended to be read consistently with the [Data Protection Act, 2021](https://www.dataprotection.gov.zm/wp-content/uploads/2024/06/Act-No.-3-The-Data-Protection-Act-2021_0-2.pdf). It is not a substitute for checking current regulations, DPC guidance, registration and DPO obligations, or the operator’s actual provider contracts and system configuration.
