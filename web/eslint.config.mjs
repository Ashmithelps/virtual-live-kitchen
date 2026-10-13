import nextVitals from "eslint-config-next/core-web-vitals";
import nextTypescript from "eslint-config-next/typescript";

const config = [
  ...nextVitals,
  ...nextTypescript,
  {
    rules: {
      // Menu images are short-lived presigned S3 URLs from arbitrary hosts; next/image would
      // proxy and cache them past expiry, so a plain <img> is the right tool here.
      "@next/next/no-img-element": "off",
    },
  },
  { ignores: [".next/**", "node_modules/**", "playwright-report/**", "test-results/**", "lib/api/schema.ts", "next-env.d.ts"] },
];

export default config;
