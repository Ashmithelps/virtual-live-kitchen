/**
 * Company and grievance-officer details for the legal pages, from environment variables set in Vercel
 * (see web/.env.example and PRODUCTION.md). Missing values render as visible placeholders.
 */
export const LEGAL = {
  entity: process.env.LEGAL_ENTITY_NAME,
  address: process.env.LEGAL_ADDRESS,
  supportEmail: process.env.LEGAL_SUPPORT_EMAIL,
  grievanceName: process.env.GRIEVANCE_OFFICER_NAME,
  grievanceEmail: process.env.GRIEVANCE_OFFICER_EMAIL,
  grievancePhone: process.env.GRIEVANCE_OFFICER_PHONE,
  jurisdiction: process.env.LEGAL_JURISDICTION_CITY,
  effectiveDate: process.env.LEGAL_EFFECTIVE_DATE,
  /** Set LEGAL_PAGES_REVIEWED=true once a lawyer has approved the text; until then a draft banner shows. */
  reviewed: process.env.LEGAL_PAGES_REVIEWED === "true",
};
