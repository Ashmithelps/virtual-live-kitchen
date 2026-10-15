import type { Metadata } from "next";
import Link from "next/link";
import { GrievanceOfficer, LegalPage, Value } from "@/components/legal/legal-page";
import { LEGAL } from "@/lib/legal";

export const metadata: Metadata = { title: "Terms of use · Virtual Live Kitchen" };

export default function TermsPage() {
  return (
    <LegalPage title="Terms of use">
      <p>
        These terms apply to Virtual Live Kitchen, run by <Value value={LEGAL.entity} what="company legal name" />. By using the service you agree to them
        and to the <Link href="/privacy" className="underline">privacy policy</Link>.
      </p>

      <h2>The service</h2>
      <p>
        We connect customers with independent restaurants. The restaurant prepares and sells the food and is responsible for its quality, safety
        and FSSAI compliance. We provide ordering, payments, kitchen clips and related features.
      </p>

      <h2>Orders, payments and refunds</h2>
      <ul>
        <li>Prices are set by the restaurant and include the taxes shown at checkout.</li>
        <li>Online payments are processed by Razorpay. If a paid order is rejected or cancelled, the full amount is refunded to the original payment method; banks may take a few working days.</li>
        <li>Dine-in &quot;pay later&quot; bills are settled with the restaurant.</li>
      </ul>

      <h2>Kitchen clips</h2>
      <ul>
        <li>Your clip is for personal use. You may download it and share it, but not remove the watermark or use it commercially.</li>
        <li>Clips are deleted after their retention period, and share links stop working when you turn them off or the clip expires.</li>
        <li>A clip is not always available, for example when the chef has not given recording consent or the camera was offline.</li>
      </ul>

      <h2>Tips</h2>
      <p>Tips go to the chef through Razorpay Route (100% unless shown otherwise at the time of tipping). Tips are voluntary and are not refunded except in case of a payment error.</p>

      <h2>Restaurants</h2>
      <ul>
        <li>Listing requires approval and a monthly subscription after the free trial. If payment fails, there is a grace period, after which the listing is hidden until payment succeeds; data is kept.</li>
        <li>Restaurants must hold a valid FSSAI licence, keep their menu and prices accurate, and obtain recording consent from every chef before they are recorded.</li>
        <li>GST invoices are issued for each subscription charge.</li>
      </ul>

      <h2>Chefs</h2>
      <p>
        Chefs are recorded only after they accept the <Link href="/recording-consent" className="underline">recording consent</Link>, which they can withdraw at any time in
        their settings.
      </p>

      <h2>Acceptable use</h2>
      <p>Do not misuse the service: no fraud, abuse, scraping, attempts to bypass security or rate limits, or uploading unlawful content.</p>

      <h2>Liability</h2>
      <p>
        The service is provided as is. To the extent the law allows, we are not liable for indirect losses, and our total liability for any claim is
        limited to the amount you paid us (or, for orders, the order value) in the three months before the claim.
      </p>

      <h2>Law and disputes</h2>
      <p>
        These terms are governed by the laws of India. Courts at <Value value={LEGAL.jurisdiction} what="city" /> have jurisdiction. Please contact
        the grievance officer first; we try to resolve complaints quickly.
      </p>

      <h2 id="grievance">Grievance officer</h2>
      <GrievanceOfficer />
      <p>
        Support: <Value value={LEGAL.supportEmail} what="support email" />
      </p>
    </LegalPage>
  );
}
