import { permanentRedirect } from "next/navigation";

/** Old address of the public chef page (shared clips from M11 link here). */
export default async function OldChefPage({ params }: { params: Promise<{ chefId: string }> }) {
  const { chefId } = await params;
  permanentRedirect(`/chef/${chefId}`);
}
