import { useQuery } from "@tanstack/react-query";
import { fetchAtlasHealth } from "../../api/client";

export function useAtlasHealth() {
  return useQuery({
    queryKey: ["atlas", "health"],
    queryFn: fetchAtlasHealth,
    retry: 1,
    staleTime: 30_000,
  });
}
