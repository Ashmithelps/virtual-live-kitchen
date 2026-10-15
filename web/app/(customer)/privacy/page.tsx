import type { Metadata } from "next";
import { GrievanceOfficer, LegalPage, Value } from "@/components/legal/legal-page";
import { LEGAL } from "@/lib/legal";

export const metadata: Metadata = { title: "Privacy policy · Virtual Live Kitchen" };

export default function PrivacyPage() {
  return (
    <LegalPage title="Privacy policy">
      <p>
        This policy explains how <Value value={LEGAL.entity} what="company legal name" /> (&quot;we&quot;), which runs Virtual Live Kitchen, handles
        personal data as a Data Fiduciary under India&apos;s Digital Personal Data Protection Act, 2023 (DPDP Act).
      </p>

      <h2>What we collect</h2>
      <ul>
        <li><strong>Account:</strong> name, mobile number, optional email, and a one-way hash of your password.</li>
        <li><strong>Orders:</strong> what you ordered, amounts, the restaurant, your table (dine-in) or delivery address and map location (delivery).</li>
        <li><strong>Payments:</strong> handled by Razorpay. We keep payment and refund references and their status, never card, UPI or bank login details.</li>
        <li><strong>Kitchen video:</strong> cameras at restaurant prep stations record chefs who have given recording consent. A short clip of your order being prepared is made for you; raw footage is deleted within 24 hours.</li>
        <li><strong>Hygiene analysis:</strong> clips are checked automatically for gloves, head covers and masks. This does not identify faces.</li>
        <li><strong>Clip views and shares:</strong> a random identifier stored in your browser, counted once per day, so we can show view counts.</li>
        <li><strong>Chefs:</strong> profile, recording-consent history (version, time, IP address) and, for tips, payout details held by Razorpay Route (we keep the legal name, PAN and the last four digits of the bank account).</li>
        <li><strong>Restaurants:</strong> business details, FSSAI licence, GSTIN, subscription and invoice records.</li>
        <li><strong>Technical:</strong> IP address and device information in server logs and error reports, kept briefly to run and secure the service.</li>
      </ul>

      <h2>Why we use it</h2>
      <ul>
        <li>To take, prepare, deliver and refund orders, and to show you your clip (with your consent, given when you use the service).</li>
        <li>To pay chefs their tips and to bill restaurants.</li>
        <li>To keep the service safe: fraud prevention, rate limits, error monitoring.</li>
        <li>To comply with law, for example tax records and lawful requests from authorities.</li>
      </ul>
      <p>You may withdraw consent at any time. Withdrawal does not affect processing already done, and some services (such as ordering) need the data to work.</p>

      <h2>Who we share it with</h2>
      <ul>
        <li>The restaurant you order from (your order, name, phone and delivery address).</li>
        <li>Service providers acting for us: Razorpay (payments), Cloudflare (file storage), our cloud host in India, Sentry (error reports, without cookies or IP addresses). Some providers may process data outside India.</li>
        <li>Anyone you share a clip link with. You can turn a link off at any time.</li>
        <li>Authorities, when the law requires it.</li>
      </ul>
      <p>We do not sell personal data.</p>

      <h2>How long we keep it</h2>
      <ul>
        <li>Order clips: 30 days by default (a restaurant&apos;s plan may set up to 90 days). Raw footage: at most 24 hours.</li>
        <li>Orders, payments and invoices: as long as tax and accounting laws require.</li>
        <li>Your account: until you ask us to delete it, unless the law requires us to keep some of it.</li>
        <li>Database backups: 30 days.</li>
      </ul>

      <h2>Your rights</h2>
      <p>
        Under the DPDP Act you can ask for a summary of your data and how it is processed, correction or completion, erasure, and grievance
        redressal, and you can nominate someone to exercise your rights. Write to the grievance officer below. If you are not satisfied with our
        response, you may complain to the Data Protection Board of India.
      </p>

      <h2>Children</h2>
      <p>The service is meant for adults. We do not knowingly process children&apos;s data without verifiable consent from a parent or guardian.</p>

      <h2>Security</h2>
      <p>
        Data is encrypted in transit and stored with access controls. Passwords and device tokens are stored only as hashes. If a personal data
        breach happens, we will inform the Data Protection Board and affected people as the law requires.
      </p>

      <h2 id="grievance">Grievance officer</h2>
      <GrievanceOfficer />

      <h2>Changes</h2>
      <p>We will post changes here and, for significant ones, tell you in the app before they apply.</p>
    </LegalPage>
  );
}
